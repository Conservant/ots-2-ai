package com.example.routerservice.service

import com.example.routerservice.config.OutboxProcessingProperties
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@Component
internal class OutboxProcessor(
    private val dispatcher: ArtemisOutboxDispatcher,
    private val properties: OutboxProcessingProperties,
    private val metrics: OutboxMetrics,
) {
    private val log = LoggerFactory.getLogger(OutboxProcessor::class.java)
    private val stopping = AtomicBoolean(false)
    private val executor: ExecutorService = Executors.newFixedThreadPool(properties.parallelWorkers)

    @Scheduled(fixedDelayString = "\${app.outbox.processor.fixed-delay-ms:5000}")
    fun scheduledPoll() {
        pollAndDispatch()
    }

    fun pollAndDispatch() {
        if (stopping.get()) {
            return
        }

        metrics.incrementPollCycles()
        val workers = (1..properties.parallelWorkers).map {
            CompletableFuture.supplyAsync(
                {
                    runCatching { dispatcher.dispatchBatch(properties.batchSize) }
                        .onFailure { metrics.incrementFailedBatches() }
                        .getOrElse {
                            log.error("Outbox dispatch batch failed", it)
                            0
                        }
                },
                executor,
            )
        }
        val processedInCycle = workers.sumOf { it.join() }.toLong()
        metrics.addProcessedEvents(processedInCycle)

        val recoveredCount = runCatching {
            dispatcher.recoverStuckEvents(properties.lockTimeoutSeconds)
        }.getOrElse {
            metrics.incrementFailedBatches()
            log.error("Outbox stuck recovery failed", it)
            0
        }
        metrics.addRecoveredEvents(recoveredCount.toLong())
    }

    fun metricsSnapshot(): OutboxMetricsSnapshot = metrics.snapshot()

    @PreDestroy
    fun shutdown() {
        stopping.set(true)
        executor.shutdown()
        if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
            executor.shutdownNow()
        }
    }
}
