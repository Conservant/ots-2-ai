package com.example.routerservice.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.outbox.processor")
internal data class OutboxProcessingProperties(
    val fixedDelayMs: Long = 5000,
    val batchSize: Int = 100,
    val parallelWorkers: Int = 4,
    val lockTimeoutSeconds: Long = 60,
)
