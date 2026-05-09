package com.example.routerservice.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "outbox_event",
    indexes = [
        Index(name = "idx_outbox_dispatch", columnList = "status,next_attempt_at,created_at"),
        Index(name = "idx_outbox_stuck", columnList = "status,locked_at"),
    ],
)
data class OutboxEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "event_id", nullable = false, unique = true)
    val eventId: UUID,
    @Column(name = "aggregate_type", nullable = false)
    val aggregateType: String,
    @Column(name = "aggregate_id", nullable = false)
    val aggregateId: String,
    @Column(name = "event_type", nullable = false)
    val eventType: String,
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    val payload: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: OutboxStatus = OutboxStatus.NEW,
    @Column(name = "retry_count", nullable = false)
    val retryCount: Int = 0,
    @Column(name = "next_attempt_at", nullable = false)
    val nextAttemptAt: Instant = Instant.now(),
    @Column(name = "last_error")
    val lastError: String? = null,
    @Column(name = "published_at")
    val publishedAt: Instant? = null,
    @Column(name = "locked_at")
    val lockedAt: Instant? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now(),
)
package com.example.routerservice.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "outbox_events")
data class OutboxEvent(
    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    val eventId: UUID,

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    val aggregateId: UUID,

    @Column(name = "event_type", nullable = false, updatable = false)
    val eventType: String,

    @Column(name = "destination", nullable = false, updatable = false)
    val destination: String,

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT", updatable = false)
    val payload: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: OutboxStatus = OutboxStatus.NEW,

    @Column(name = "retry_count", nullable = false)
    var retryCount: Int = 0,

    @Column(name = "next_retry_at", nullable = false)
    var nextRetryAt: Instant = Instant.now(),

    @Column(name = "last_error")
    var lastError: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)
