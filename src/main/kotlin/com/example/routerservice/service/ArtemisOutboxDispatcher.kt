package com.example.routerservice.service

import com.example.routerservice.domain.OutboxEvent
import com.example.routerservice.domain.OutboxStatus
import com.example.routerservice.mapper.toMessageDto
import com.example.routerservice.repository.OutboxEventRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.jms.core.JmsTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
internal class ArtemisOutboxDispatcher(
    private val outboxEventRepository: OutboxEventRepository,
    private val jmsTemplate: JmsTemplate,
    @Value("\${app.outbox.destination:orders.outbox.queue}")
    private val destination: String,
) {
    private val log = LoggerFactory.getLogger(ArtemisOutboxDispatcher::class.java)

    @Transactional
    fun dispatchBatch(batchSize: Int): Int {
        val now = Instant.now()
        val events = outboxEventRepository.lockBatchForDispatch(now = now, limit = batchSize)

        events.forEach { event ->
            dispatchSingle(event = event, now = now)
        }
        return events.size
    }

    @Transactional
    fun recoverStuckEvents(lockTimeoutSeconds: Long): Int {
        val threshold = Instant.now().minus(lockTimeoutSeconds, ChronoUnit.SECONDS)
        val stuckEvents = outboxEventRepository.findAllByStatusAndLockedAtBefore(
            status = OutboxStatus.IN_PROGRESS,
            lockedAt = threshold,
        )
        val now = Instant.now()
        stuckEvents.forEach { event ->
            outboxEventRepository.save(
                event.copy(
                    status = OutboxStatus.RETRY,
                    nextAttemptAt = now,
                    lockedAt = null,
                    updatedAt = now,
                ),
            )
        }
        return stuckEvents.size
    }

    private fun dispatchSingle(event: OutboxEvent, now: Instant) {
        val inProgress = event.copy(
            status = OutboxStatus.IN_PROGRESS,
            lockedAt = now,
            updatedAt = now,
        )
        outboxEventRepository.save(inProgress)

        runCatching {
            jmsTemplate.convertAndSend(destination, inProgress.toMessageDto()) { message ->
                message.setStringProperty("eventId", inProgress.eventId.toString())
                message
            }
        }.onSuccess {
            outboxEventRepository.save(
                inProgress.copy(
                    status = OutboxStatus.PUBLISHED,
                    publishedAt = Instant.now(),
                    lastError = null,
                    lockedAt = null,
                    updatedAt = Instant.now(),
                ),
            )
        }.onFailure { error ->
            log.error("Failed to dispatch outbox event id={}", inProgress.id, error)
            val nextRetryCount = inProgress.retryCount + 1
            val backoffSeconds = calculateBackoffSeconds(nextRetryCount)
            val failedStatus = if (nextRetryCount >= MAX_RETRY_COUNT) {
                OutboxStatus.DEAD
            } else {
                OutboxStatus.RETRY
            }
            outboxEventRepository.save(
                inProgress.copy(
                    status = failedStatus,
                    retryCount = nextRetryCount,
                    nextAttemptAt = Instant.now().plus(backoffSeconds, ChronoUnit.SECONDS),
                    lastError = error.message?.take(MAX_ERROR_LENGTH),
                    lockedAt = null,
                    updatedAt = Instant.now(),
                ),
            )
        }
    }

    private fun calculateBackoffSeconds(retryCount: Int): Long {
        val exponent = retryCount.coerceAtMost(10)
        return (1L shl exponent).coerceAtMost(MAX_BACKOFF_SECONDS)
    }

    private companion object {
        private const val MAX_RETRY_COUNT = 10
        private const val MAX_BACKOFF_SECONDS = 300L
        private const val MAX_ERROR_LENGTH = 1000
    }
}
