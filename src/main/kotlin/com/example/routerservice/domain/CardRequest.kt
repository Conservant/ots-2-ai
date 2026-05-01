package com.example.routerservice.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "card_requests")
data class CardRequest(
    @Id
    val id: UUID,

    @Column(name = "request_id", nullable = false)
    val requestId: UUID,

    @Column(name = "card_id", nullable = false)
    val cardId: String,

    @Column(name = "client_id", nullable = false)
    val clientId: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val type: CardRequestType,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: CardRequestStatus = CardRequestStatus.RECEIVED,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at")
    var updatedAt: Instant = Instant.now()
)
