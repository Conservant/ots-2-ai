package com.example.routerservice.domain

import java.time.Instant
import java.util.UUID

internal data class OutboxMessageDto(
    val eventId: UUID,
    val aggregateType: String,
    val aggregateId: String,
    val eventType: String,
    val payload: String,
    val createdAt: Instant,
)
