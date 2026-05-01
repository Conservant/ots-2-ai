package com.example.routerservice.listener

import com.example.routerservice.domain.CardRequestMessage
import com.example.routerservice.domain.CardRequestStatus
import com.example.routerservice.domain.CardRequestType
import com.example.routerservice.repository.CardRequestRepository
import io.kotest.core.spec.style.DescribeSpec
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
import java.util.UUID

@SpringBootTest
@Testcontainers
class CardRequestListenerIntegrationTest(
    private val jmsTemplate: JmsTemplate,
    private val repository: CardRequestRepository
) : DescribeSpec({

    describe("CardRequestListener integration") {

        it("publishes a message to card.requests and the listener persists it with status RECEIVED then ROUTED") {
            val message = CardRequestMessage(
                cardId = "CARD-${UUID.randomUUID()}",
                clientId = "CLIENT-${UUID.randomUUID()}",
                type = CardRequestType.OPEN,
                requestedAt = Instant.now()
            )

            jmsTemplate.convertAndSend("card.requests", message)

            // Poll up to 5 seconds for the record to appear
            var found = false
            repeat(25) {
                if (!found) {
                    val records = repository.findAll().filter { it.cardId == message.cardId }
                    if (records.isNotEmpty()) {
                        found = true
                        val record = records.first()
                        record.cardId shouldBe message.cardId
                        record.clientId shouldBe message.clientId
                        record.type shouldBe message.type
                        record.status shouldBe CardRequestStatus.ROUTED
                    } else {
                        Thread.sleep(200)
                    }
                }
            }
            found shouldBe true
        }

        it("routes OPEN type to card.open queue") {
            val message = CardRequestMessage(
                cardId = "CARD-${UUID.randomUUID()}",
                clientId = "CLIENT-${UUID.randomUUID()}",
                type = CardRequestType.OPEN,
                requestedAt = Instant.now()
            )

            jmsTemplate.convertAndSend("card.requests", message)

            // Wait for processing
            Thread.sleep(2000)

            jmsTemplate.receiveTimeout = 3000
            val received = jmsTemplate.receive("card.open")
            received shouldNotBe null
        }

        it("routes CLOSE type to card.close queue") {
            val message = CardRequestMessage(
                cardId = "CARD-${UUID.randomUUID()}",
                clientId = "CLIENT-${UUID.randomUUID()}",
                type = CardRequestType.CLOSE,
                requestedAt = Instant.now()
            )

            jmsTemplate.convertAndSend("card.requests", message)

            Thread.sleep(2000)

            jmsTemplate.receiveTimeout = 3000
            val received = jmsTemplate.receive("card.close")
            received shouldNotBe null
        }

        it("routes BLOCK type to card.block queue") {
            val message = CardRequestMessage(
                cardId = "CARD-${UUID.randomUUID()}",
                clientId = "CLIENT-${UUID.randomUUID()}",
                type = CardRequestType.BLOCK,
                requestedAt = Instant.now()
            )

            jmsTemplate.convertAndSend("card.requests", message)

            Thread.sleep(2000)

            jmsTemplate.receiveTimeout = 3000
            val received = jmsTemplate.receive("card.block")
            received shouldNotBe null
        }

        it("routes UNBLOCK type to card.unblock queue") {
            val message = CardRequestMessage(
                cardId = "CARD-${UUID.randomUUID()}",
                clientId = "CLIENT-${UUID.randomUUID()}",
                type = CardRequestType.UNBLOCK,
                requestedAt = Instant.now()
            )

            jmsTemplate.convertAndSend("card.requests", message)

            Thread.sleep(2000)

            jmsTemplate.receiveTimeout = 3000
            val received = jmsTemplate.receive("card.unblock")
            received shouldNotBe null
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
        }
    }
}
