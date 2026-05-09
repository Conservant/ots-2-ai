# Выполнение задачи по добавлению шедулера

Агент выполнял задачу сначала без использования системы правил, затем с использованием.

В обоих случаях агент создал миграцию для создания таблицы для Shedlock и добавил конфиг для шедулера в файл проекта

    CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMPTZ  NOT NULL,
    locked_at  TIMESTAMPTZ  NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    CONSTRAINT pk_shedlock PRIMARY KEY (name)
    );

Созданный шедулер немного отличался.
С правилами агент вынес знаяения для блокировки шедулера в переменные окружения, что является более гибким решением

```kotlin
@Component
class CardRequestRetryScheduler(
private val cardRequestService: CardRequestService,
@Value("\${app.card-request-retry.batch-size:100}")
private val batchSize: Int,
) {

    private val log = LoggerFactory.getLogger(CardRequestRetryScheduler::class.java)

    @Scheduled(cron = "\${app.card-request-retry.cron:0 */1 * * * *}")
    @SchedulerLock(
        name = "cardRequestRetryScheduler_retryFailed",
        lockAtMostFor = "\${app.card-request-retry.lock-at-most-for:PT5M}",
        lockAtLeastFor = "\${app.card-request-retry.lock-at-least-for:PT10S}",
    )
    fun retryFailed() {
        val retryCount = cardRequestService.retryFailed(batchSize)

        if (retryCount > 0) {
            log.info("Retried {} failed card requests", retryCount)
        }
    }
}
```

Без правил:
```kotlin
@Component
class CardRequestRetryScheduler(
private val cardRequestService: CardRequestService,
@Value("\${app.card-request-retry.batch-size:100}")
private val batchSize: Int,
) {
private val log = LoggerFactory.getLogger(CardRequestRetryScheduler::class.java)

    @Scheduled(fixedDelayString = "\${app.card-request-retry.fixed-delay-ms:60000}")
    @SchedulerLock(
        name = "cardRequestRetryScheduler_retryFailedRequests",
        lockAtLeastFor = "PT5S",
        lockAtMostFor = "PT55S",
    )
    fun retryFailedRequests() {
        val retriedCount = cardRequestService.retryFailedRequests(batchSize)
        if (retriedCount > 0) {
            log.info("Retried and routed {} failed card request(s)", retriedCount)
        }
    }
}
```

В классе CardRequestServiceImpl.kt
агент создал методв для обрабоки заявок со статусом FAILED. 
В случае с правилами агент вынес логику по обновлению статуса в отдельный метод

С правилами
```kotlin
fun retryFailed(batchSize: Int): Int {
val failedRequests = repository.findAllByStatusOrderByUpdatedAtAsc(
status = CardRequestStatus.FAILED,
pageable = PageRequest.of(0, batchSize),
)

        failedRequests.forEach { failedRequest ->
            try {
                router.route(failedRequest)
                updateStatus(failedRequest, CardRequestStatus.ROUTED)
            } catch (e: Exception) {
                log.error("Retry failed for card request ${failedRequest.id}", e)
                updateStatus(failedRequest, CardRequestStatus.FAILED)
            }
        }

        return failedRequests.size
    }

    private fun updateStatus(cardRequest: CardRequest, status: CardRequestStatus) {
        cardRequest.status = status
        cardRequest.updatedAt = Instant.now()
        repository.save(cardRequest)
    }

```
Без правил
```kotlin
fun retryFailedRequests(batchSize: Int): Int {
if (batchSize <= 0) {
return 0
}

            val failedRequests = repository.findByStatus(
                CardRequestStatus.FAILED,
                PageRequest.of(0, batchSize),
            )
            if (failedRequests.isEmpty()) {
                return 0
            }

            failedRequests.forEach { failedRequest ->
                try {
                    router.route(failedRequest)
                    failedRequest.status = CardRequestStatus.ROUTED
                    failedRequest.updatedAt = Instant.now()
                    repository.save(failedRequest)
                } catch (e: Exception) {
                    log.error("Retry routing failed for card request ${failedRequest.id}", e)
                    failedRequest.updatedAt = Instant.now()
                    repository.save(failedRequest)
                }
            }

            return failedRequests.count { it.status == CardRequestStatus.ROUTED }
        }
```

Задача небольшая, но при этом можно увидеть, что в релизации есть различия. Код агента с правилами получился более гибким. 
Методы получились короткими, а значит более читаемыми.


# Реализация паттерна Transactional Outbox
## Реализация паттерна Transactional Outbox для надежной отправки событий в микросервисной архитектуре
Бизнес-контекст

При получении заявки в системе необходимо:

    1. Сохранить заявку в БД
    2. Отправить событие в исходящую для других сервисов (уведомления, инвентаризация, аналитика)
    3. Гарантировать, что событие будет отправлено даже при падении сервера после сохранения заказа

### Сохранить заявку в БД

Промпт:

     **Role (Роль):**
    Ты — Senior Java/Kotlin Developer и архитектор решений с опытом реализации паттерна Transactional Outbox в микросервисной архитектуре на Spring Boot, PostgreSQL и Artemis.
    
    **Task (Задача):**
    Спроектировать архитектуру и структуру БД для реализации паттерна Transactional Outbox. Необходимо создать таблицу outbox для хранения событий, которые будут отправлены в Artemis. Должна быть поддержка:
    - Атомарности сохранения заказа и события (одна транзакция)
      - Повторной отправки при неудачах
      - Мониторинга зависших событий
      - Partitioning для больших нагрузок
    
    **Context (Контекст):**
    - База данных: PostgreSQL 14+
      - Существующая таблица: orders (id BIGSERIAL, status VARCHAR, created_at TIMESTAMP)
      - Требования к производительности: до 1000 событий/сек
      - Требования к надежности: zero loss, идемпотентная обработка
      - Очистка старых событий: через 7 дней
    
    **Format (Формат):**
    1. **ER-диаграмма** (текстовое описание связей)
       2. **SQL схема таблицы outbox** (Liquibase changeset в YAML формате)
       3. **Индексы и партиционирование** (с обоснованием)
       4. **DTO и модели** (Kotlin data classes)
       5. **Sequence диаграмма** (текстовое описание потока)

### Результат: 


1) ER-диаграмма (текстом)
Сущности и связи:

        orders (уже есть): бизнес-данные заказа.
        outbox_event (новая): события для отправки в Artemis.
        Связь: orders (1) -> (N) outbox_event по outbox_event.order_id = orders.id.
        Логика полей outbox_event:
    
        Идентификация: id (внутренний PK), event_id (глобальный UUID для идемпотентности).
        Бизнес-контекст: aggregate_type, aggregate_id, event_type, order_id.
        Данные события: payload (jsonb), headers (jsonb).
        Доставка: status, attempt_count, next_attempt_at, published_at, last_error.
        Техническое: created_at, updated_at, locked_by, locked_at.
        Статусы (пример):
    
        NEW -> IN_PROGRESS -> PUBLISHED
        при ошибке: IN_PROGRESS -> RETRY (с backoff)
        при исчерпании попыток: DEAD
    
2) SQL схема outbox (Liquibase YAML)
Ниже changeset с range partitioning по created_at (дневные партиции), FK на orders, и CHECK на status.
```yaml
databaseChangeLog:
- changeSet:
  id: 2026-05-09-01-create-outbox-event
  author: ai-architect
  changes:
  - sql:
  splitStatements: false
  stripComments: true
  sql: |
      CREATE TABLE IF NOT EXISTS outbox_event (
      id BIGINT GENERATED BY DEFAULT AS IDENTITY,
      event_id UUID NOT NULL,
      order_id BIGINT NOT NULL,
      aggregate_type VARCHAR(100) NOT NULL,
      aggregate_id VARCHAR(100) NOT NULL,
      event_type VARCHAR(150) NOT NULL,
      payload JSONB NOT NULL,
      headers JSONB NOT NULL DEFAULT '{}'::jsonb,
      status VARCHAR(20) NOT NULL,
      attempt_count INTEGER NOT NULL DEFAULT 0,
      max_attempts INTEGER NOT NULL DEFAULT 20,
      next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      published_at TIMESTAMPTZ NULL,
      last_error TEXT NULL,
      locked_by VARCHAR(100) NULL,
      locked_at TIMESTAMPTZ NULL,
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      CONSTRAINT pk_outbox_event PRIMARY KEY (id, created_at),
      CONSTRAINT uq_outbox_event_event_id UNIQUE (event_id, created_at),
      CONSTRAINT fk_outbox_event_order_id FOREIGN KEY (order_id) REFERENCES orders(id),
      CONSTRAINT chk_outbox_event_status CHECK (
      status IN ('NEW', 'IN_PROGRESS', 'RETRY', 'PUBLISHED', 'DEAD')
      ),
      CONSTRAINT chk_outbox_event_attempt_count_non_negative CHECK (attempt_count >= 0),
      CONSTRAINT chk_outbox_event_max_attempts_positive CHECK (max_attempts > 0)
      ) PARTITION BY RANGE (created_at);
- changeSet:
  id: 2026-05-09-02-create-outbox-partition-today
  author: ai-architect
  changes:
  - sql:
  sql: |
    CREATE TABLE IF NOT EXISTS outbox_event_p20260509
    PARTITION OF outbox_event
    FOR VALUES FROM ('2026-05-09 00:00:00+00') TO ('2026-05-10 00:00:00+00');
- changeSet:
  id: 2026-05-09-03-create-outbox-indexes-parent
  author: ai-architect
  changes:
  - sql:
  splitStatements: true
  sql: |
      CREATE INDEX IF NOT EXISTS idx_outbox_event_dispatch
      ON outbox_event (status, next_attempt_at, created_at);
      CREATE INDEX IF NOT EXISTS idx_outbox_event_stuck
      ON outbox_event (status, locked_at)
      WHERE status = 'IN_PROGRESS';
      CREATE INDEX IF NOT EXISTS idx_outbox_event_order_id
      ON outbox_event (order_id);
      CREATE INDEX IF NOT EXISTS idx_outbox_event_published_at
      ON outbox_event (published_at)
      WHERE status = 'PUBLISHED';
```

3) Индексы и партиционирование (с обоснованием)

        idx_outbox_event_dispatch (status, next_attempt_at, created_at)
        Для polling-запроса издателя: быстро выбрать NEW/RETRY, готовые к отправке (next_attempt_at <= now()), в порядке FIFO.
        
        idx_outbox_event_stuck (status, locked_at) WHERE status='IN_PROGRESS'
        Для мониторинга зависших: найти события, которые долго в работе (воркер умер/завис).
        
        idx_outbox_event_order_id
        Для трассировки по заказу и расследований.
        
        idx_outbox_event_published_at WHERE status='PUBLISHED'
        Для быстрой очистки/архивации опубликованных событий.
        
        Partitioning: RANGE(created_at) по дням
        При 1000 evt/s это десятки миллионов/день. Дневные партиции дают:
        
        быстрый DROP PARTITION для retention 7 дней;
        меньше bloat и быстрее vacuum/analyze на активной партиции;
        стабильнее latency у polling-запросов.
        Рекомендация по эксплуатации:
        
        Джоб раз в сутки: создать партиции на 3–7 дней вперед.
        Джоб очистки: удалять партиции старше 7 дней (мгновенно, без массового DELETE).
        
        DTO и модели (Kotlin data classes)

) Sequence-диаграмма (текстовое описание потока)

    API -> OrderService: приходит команда создать/изменить заказ.
    OrderService (1 DB transaction):
    пишет/обновляет orders;
    формирует доменное событие (OrderCreated, OrderStatusChanged);
    вставляет запись в outbox_event со статусом NEW;
    commit.
    => атомарность: либо сохранены и заказ, и событие, либо ничего.
    OutboxPublisher (scheduler, N workers) периодически делает выборку:
    SELECT ... FOR UPDATE SKIP LOCKED по status IN (NEW, RETRY) и next_attempt_at <= now();
    помечает выбранные как IN_PROGRESS, проставляет locked_by/locked_at.
    Publisher -> Artemis: отправка сообщения с event_id в headers.
    Если отправка успешна:
    status = PUBLISHED, published_at = now(), updated_at = now().
    Если ошибка отправки:
    attempt_count = attempt_count + 1;
    если attempt_count < max_attempts: status = RETRY, next_attempt_at = now() + backoff;
    иначе status = DEAD (для ручного разбора/алертов).
    Monitoring job:
    ищет IN_PROGRESS с locked_at < now() - threshold (например, 2 мин);
    переводит обратно в RETRY или шлет алерт.
    Retention job:
    удаляет/дропает партиции старше 7 дней.



## Реализация репозитория и доработка сервиса отправки сообщений

Промпт:

    **Role (Роль):**
    Ты — Senior Java/Kotlin Developer, специализирующийся на Spring Data JPA, Kotlin Coroutines и Artemis интеграциях.
    
    **Task (Задача):**
    Реализовать репозиторий для работы с outbox таблицей и сервис для атомарного сохранения бизнес-данных вместе с outbox событиями. 
    Также реализовать сервис отправки сообщений в Artemis с обработкой ошибок и retry logic.
    
    **Context (Контекст):**
    - Сущности: OrderEntity (существующая), OutboxEvent (из шага 1)
      - Artemis: Spring Artemis с JsonSerializer
      - Транзакции: @Transactional обязателен
      - Обработка ошибок: при ошибке отправки не удалять outbox запись, а инкрементировать retry_count
      - Идемпотентность: outbox события не должны дублироваться (unique constraint на event_id)
      - Тесты: BDD на Kotest
    
    **Format (Формат):**
    1. **OutboxRepository** (Spring Data JPA с методами для поиска неотправленных)
       2. **OrderService** (модифицированный - атомарное сохранение заказа и события)
       3. **ArtemisOutboxDispatcher** (сервис отправки в Artemis)
       4. **Мапперы** (Entity ↔ DTO, Entity ↔ Domain)
       5. **Тесты** (Kotest с Given-When-Then)

## Что добавлено

Агент разработал полноценную реализацию: 
добавив OutboxRepository, атомарный сервис сохранения и ArtemisOutboxDispatcher с retry + BDD тестами.
Далее добавил новый outbox-модуль в текущую архитектуру через новые сущности/репозитории/сервисы.
И написал BDD-тесты на Kotest


## Реализация фонового процесса (Polling Publisher)

Промпт:

    **Role (Роль):**
    Ты — Senior Java/Kotlin Developer, эксперт по фоновым процессам, шедулингу и конкурентности в Spring Boot.
    
    **Task (Задача):**
    Реализовать фоновый процесс (Polling Publisher), 
    который периодически забирает неотправленные события из outbox таблицы и отправляет их в Artemis. 
    Использовать Spring @Scheduled или BackgroundScheduler. Реализовать:
    - Периодический опрос БД (каждые 5 секунд)
      - Параллельную обработку батчами (по 100 событий)
      - Exponential backoff для повторных попыток
      - Graceful shutdown
    
    **Context (Контекст):**
    - Repository: OutboxRepository (из шага 2)
      - Dispatcher: ArtemisOutboxDispatcher (из шага 2)
      - БД: PostgreSQL с поддержкой SELECT FOR UPDATE SKIP LOCKED
      - Конфигурация: через application.yml
      - Обработка дубликатов: идемпотентность на стороне потребителя
    
    **Format (Формат):**
    1. **OutboxProcessor** (@Component с шедулером)
       2. **OutboxMetrics** (класс для метрик)
       3. **Конфигурация** (application.yml)
       4. **Тесты** (интеграционные с TestContainers)


## Что добавлено

OutboxProcessor в src/main/kotlin/com/example/routerservice/service/OutboxProcessor.kt

@Scheduled polling каждые 5 сек (конфигурируется).
Параллельные воркеры (parallelWorkers), каждый обрабатывает батч (batchSize, по умолчанию 100).
После dispatch запускает recovery зависших IN_PROGRESS событий.
Graceful shutdown через @PreDestroy + ExecutorService.awaitTermination(...).
Метрики цикла доступны через metricsSnapshot().
OutboxMetrics в src/main/kotlin/com/example/routerservice/service/OutboxMetrics.kt

Счётчики: poll cycles, processed events, failed batches, recovered events.
Потокобезопасная реализация на AtomicLong.
Конфигурация scheduling/properties

```
@EnableScheduling
@EnableConfigurationProperties(OutboxProcessingProperties::class)
OutboxProcessingProperties
```
добавлен блок:

```
app.outbox.destination
app.outbox.processor.fixed-delay-ms
app.outbox.processor.batch-size
app.outbox.processor.parallel-workers
app.outbox.processor.lock-timeout-seconds
```

Интеграция с Artemis converter

src/main/kotlin/com/example/routerservice/config/JmsConfig.kt
Добавлен type mapping для OutboxMessageDto, чтобы outbox-сообщения корректно сериализовались/десериализовались.
Dispatcher + retry/backoff

```
src/main/kotlin/com/example/routerservice/service/ArtemisOutboxDispatcher.kt
Использует SELECT ... FOR UPDATE SKIP LOCKED через OutboxEventRepository.
```
При ошибке отправки:

      запись не удаляется,
      увеличивается retryCount,
      рассчитывается exponential backoff,
      событие переводится в RETRY или DEAD при достижении лимита.
Есть recoverStuckEvents(...) для зависших IN_PROGRESS.

Тесты (Testcontainers + BDD)

Добавлен BehaviorSpec:

    src/test/kotlin/com/example/routerservice/service/OutboxProcessorIntegrationTest.kt

Покрывает:

      Given NEW event -> When pollAndDispatch() -> Then PUBLISHED + сообщение в Artemis queue
      Given stale IN_PROGRESS event -> When pollAndDispatch() -> Then событие восстановлено в RETRY
      Используются контейнеры PostgreSQL + Artemis через @Testcontainers.

