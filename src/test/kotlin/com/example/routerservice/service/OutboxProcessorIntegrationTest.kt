package com.example.routerservice.service

import com.example.routerservice.domain.OutboxEvent
import com.example.routerservice.domain.OutboxStatus
import com.example.routerservice.repository.OutboxEventRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jms.core.JmsTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.activemq.ArtemisContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@SpringBootTest
@Testcontainers
class OutboxProcessorIntegrationTest(
    private val outboxEventRepository: OutboxEventRepository,
    private val outboxProcessor: OutboxProcessor,
    private val jmsTemplate: JmsTemplate,
) : BehaviorSpec({

    beforeTest {
        outboxEventRepository.deleteAll()
    }

    Given("a NEW outbox event exists and is ready to be sent") {
        val event = outboxEventRepository.save(
            OutboxEvent(
                eventId = UUID.randomUUID(),
                aggregateType = "Order",
                aggregateId = "501",
                eventType = "OrderCreated",
                payload = """{"orderId":501}""",
                status = OutboxStatus.NEW,
                nextAttemptAt = Instant.now().minus(1, ChronoUnit.MINUTES),
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            ),
        )

        When("processor poll cycle runs") {
            outboxProcessor.pollAndDispatch()

            Then("event is marked as PUBLISHED and message is sent to Artemis") {
                val stored = outboxEventRepository.findById(requireNotNull(event.id)).orElseThrow()
                stored.status shouldBe OutboxStatus.PUBLISHED
                stored.publishedAt shouldNotBe null

                jmsTemplate.receiveTimeout = 5_000
                val message = jmsTemplate.receive("orders.outbox.queue")
                message shouldNotBe null
            }
        }
    }

    Given("an IN_PROGRESS event is stale") {
        outboxEventRepository.save(
            OutboxEvent(
                eventId = UUID.randomUUID(),
                aggregateType = "Order",
                aggregateId = "777",
                eventType = "OrderCreated",
                payload = """{"orderId":777}""",
                status = OutboxStatus.IN_PROGRESS,
                retryCount = 1,
                nextAttemptAt = Instant.now().plus(1, ChronoUnit.HOURS),
                lockedAt = Instant.now().minus(5, ChronoUnit.MINUTES),
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            ),
        )

        When("processor poll cycle runs") {
            outboxProcessor.pollAndDispatch()

            Then("stuck event is recovered for retry") {
                val recovered = outboxEventRepository.findAll().filter { it.aggregateId == "777" }
                recovered shouldHaveSize 1
                recovered.first().status shouldBe OutboxStatus.RETRY
            }
        }
    }
}) {
    companion object {
        @Container
        @JvmStatic
        val artemis = ArtemisContainer("apache/activemq-artemis:2.30.0-alpine")

        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:15-alpine")

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.artemis.mode") { "native" }
            registry.add("spring.artemis.broker-url", artemis::getBrokerUrl)
            registry.add("spring.artemis.user", artemis::getUser)
            registry.add("spring.artemis.password", artemis::getPassword)
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("app.outbox.processor.fixed-delay-ms") { "600000" }
            registry.add("app.outbox.processor.batch-size") { "100" }
            registry.add("app.outbox.processor.parallel-workers") { "2" }
            registry.add("app.outbox.processor.lock-timeout-seconds") { "30" }
            registry.add("app.outbox.destination") { "orders.outbox.queue" }
        }
    }
}
