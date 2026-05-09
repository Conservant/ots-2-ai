package com.example.routerservice.service

import com.example.routerservice.domain.OutboxEvent
import com.example.routerservice.domain.OutboxStatus
import com.example.routerservice.repository.OutboxEventRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.springframework.jms.core.JmsTemplate
import java.time.Instant
import java.util.UUID

class ArtemisOutboxDispatcherTest : DescribeSpec({
    fun baseEvent() = OutboxEvent(
        id = 1L,
        eventId = UUID.randomUUID(),
        aggregateType = "Order",
        aggregateId = "99",
        eventType = "OrderCreated",
        payload = """{"id":99}""",
        status = OutboxStatus.NEW,
        retryCount = 0,
        nextAttemptAt = Instant.now(),
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    describe("dispatchBatch()") {
        it("Given send success When dispatching Then event becomes published") {
            val repository = org.mockito.Mockito.mock(OutboxEventRepository::class.java)
            val jmsTemplate = org.mockito.Mockito.mock(JmsTemplate::class.java)
            val dispatcher = ArtemisOutboxDispatcher(repository, jmsTemplate, "orders.outbox.queue")
            val event = baseEvent()

            org.mockito.Mockito.`when`(repository.lockBatchForDispatch(org.mockito.Mockito.any(), org.mockito.Mockito.eq(10)))
                .thenReturn(listOf(event))
            org.mockito.Mockito.`when`(repository.save(org.mockito.Mockito.any(OutboxEvent::class.java)))
                .thenAnswer { it.arguments[0] }

            val processed = dispatcher.dispatchBatch(10)

            processed shouldBe 1
            org.mockito.Mockito.verify(jmsTemplate, org.mockito.Mockito.times(1))
                .convertAndSend(
                    org.mockito.Mockito.eq("orders.outbox.queue"),
                    org.mockito.Mockito.any(),
                    org.mockito.Mockito.any(),
                )
            org.mockito.Mockito.verify(repository, org.mockito.Mockito.atLeast(2))
                .save(org.mockito.Mockito.any(OutboxEvent::class.java))
        }

        it("Given send failure When dispatching Then retry count increments and status changes to retry or dead") {
            val repository = org.mockito.Mockito.mock(OutboxEventRepository::class.java)
            val jmsTemplate = org.mockito.Mockito.mock(JmsTemplate::class.java)
            val dispatcher = ArtemisOutboxDispatcher(repository, jmsTemplate, "orders.outbox.queue")
            val event = baseEvent()

            org.mockito.Mockito.`when`(repository.lockBatchForDispatch(org.mockito.Mockito.any(), org.mockito.Mockito.eq(1)))
                .thenReturn(listOf(event))
            org.mockito.Mockito.`when`(repository.save(org.mockito.Mockito.any(OutboxEvent::class.java)))
                .thenAnswer { it.arguments[0] }
            org.mockito.Mockito.doThrow(RuntimeException("broker unavailable"))
                .`when`(jmsTemplate)
                .convertAndSend(
                    org.mockito.Mockito.eq("orders.outbox.queue"),
                    org.mockito.Mockito.any(),
                    org.mockito.Mockito.any(),
                )

            dispatcher.dispatchBatch(1)

            val captor = org.mockito.ArgumentCaptor.forClass(OutboxEvent::class.java)
            org.mockito.Mockito.verify(repository, org.mockito.Mockito.atLeast(2)).save(captor.capture())
            val failedVersion = captor.allValues.last()
            failedVersion.retryCount shouldBeGreaterThan 0
            (failedVersion.status == OutboxStatus.RETRY || failedVersion.status == OutboxStatus.DEAD) shouldBe true
        }
    }
})
package com.example.routerservice.service

import com.example.routerservice.domain.OutboxEvent
import com.example.routerservice.domain.OutboxStatus
import com.example.routerservice.repository.OutboxRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.`when`
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.jms.core.JmsTemplate
import java.time.Instant
import java.util.UUID

class ArtemisOutboxDispatcherTest : BehaviorSpec({
    fun newEvent() = OutboxEvent(
        eventId = UUID.randomUUID(),
        aggregateId = UUID.randomUUID(),
        eventType = "CardRequestReceived",
        destination = "card.open",
        payload = """{"id":"value"}""",
        status = OutboxStatus.NEW,
        retryCount = 0,
        nextRetryAt = Instant.now(),
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
    )

    Given("pending outbox event and successful JMS send") {
        val repository = mock(OutboxRepository::class.java)
        val jmsTemplate = mock(JmsTemplate::class.java)
        val dispatcher = ArtemisOutboxDispatcher(repository, jmsTemplate)
        val event = newEvent()
        `when`(repository.lockBatchForDispatch(any(), any())).thenReturn(listOf(event))
        `when`(repository.saveAll(any<List<OutboxEvent>>())).thenAnswer { it.arguments[0] as List<OutboxEvent> }

        When("dispatchPending is called") {
            val sentCount = dispatcher.dispatchPending(20)

            Then("event is marked as SENT and sent to Artemis") {
                sentCount shouldBe 1
                event.status shouldBe OutboxStatus.SENT
                verify(jmsTemplate, times(1)).convertAndSend(event.destination, event.payload)
                verify(repository, times(1)).saveAll(any<List<OutboxEvent>>())
            }
        }
    }

    Given("pending outbox event and JMS send failure") {
        val repository = mock(OutboxRepository::class.java)
        val jmsTemplate = mock(JmsTemplate::class.java)
        val dispatcher = ArtemisOutboxDispatcher(repository, jmsTemplate)
        val event = newEvent()
        `when`(repository.lockBatchForDispatch(any(), any())).thenReturn(listOf(event))
        doThrow(RuntimeException("broker down")).`when`(jmsTemplate).convertAndSend(any<String>(), any<String>())
        `when`(repository.saveAll(any<List<OutboxEvent>>())).thenAnswer { it.arguments[0] as List<OutboxEvent> }

        When("dispatchPending is called") {
            val sentCount = dispatcher.dispatchPending(20)

            Then("event remains for retry with incremented retryCount") {
                sentCount shouldBe 0
                event.status shouldBe OutboxStatus.FAILED
                event.retryCount shouldBe 1
                verify(repository, times(1)).saveAll(any<List<OutboxEvent>>())
            }
        }
    }
})
