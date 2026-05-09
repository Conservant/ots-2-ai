package com.example.routerservice.mapper

import com.example.routerservice.domain.OrderEntity
import com.example.routerservice.domain.OutboxEvent
import com.example.routerservice.domain.OutboxMessageDto
import com.example.routerservice.domain.OutboxStatus
import java.time.Instant
import java.util.UUID

private const val ORDER_AGGREGATE_TYPE = "Order"
private const val ORDER_CREATED_EVENT_TYPE = "OrderCreated"

internal fun OrderEntity.toCreatedOutboxEvent(
    eventId: UUID,
    payload: String,
    now: Instant,
): OutboxEvent = OutboxEvent(
    eventId = eventId,
    aggregateType = ORDER_AGGREGATE_TYPE,
    aggregateId = requireNotNull(id).toString(),
    eventType = ORDER_CREATED_EVENT_TYPE,
    payload = payload,
    status = OutboxStatus.NEW,
    retryCount = 0,
    nextAttemptAt = now,
    createdAt = now,
    updatedAt = now,
)

internal fun OutboxEvent.toMessageDto(): OutboxMessageDto = OutboxMessageDto(
    eventId = eventId,
    aggregateType = aggregateType,
    aggregateId = aggregateId,
    eventType = eventType,
    payload = payload,
    createdAt = createdAt,
)
