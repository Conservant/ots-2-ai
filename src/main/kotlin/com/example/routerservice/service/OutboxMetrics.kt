package com.example.routerservice.service

import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong

@Component
internal class OutboxMetrics {
    private val pollCycles = AtomicLong(0)
    private val processedEvents = AtomicLong(0)
    private val failedBatches = AtomicLong(0)
    private val recoveredEvents = AtomicLong(0)

    fun incrementPollCycles() {
        pollCycles.incrementAndGet()
    }

    fun addProcessedEvents(count: Long) {
        processedEvents.addAndGet(count)
    }

    fun incrementFailedBatches() {
        failedBatches.incrementAndGet()
    }

    fun addRecoveredEvents(count: Long) {
        recoveredEvents.addAndGet(count)
    }

    fun snapshot(): OutboxMetricsSnapshot = OutboxMetricsSnapshot(
        pollCycles = pollCycles.get(),
        processedEvents = processedEvents.get(),
        failedBatches = failedBatches.get(),
        recoveredEvents = recoveredEvents.get(),
    )
}

internal data class OutboxMetricsSnapshot(
    val pollCycles: Long,
    val processedEvents: Long,
    val failedBatches: Long,
    val recoveredEvents: Long,
)
