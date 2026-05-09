Выполнение задачи по добавлению шедулера

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