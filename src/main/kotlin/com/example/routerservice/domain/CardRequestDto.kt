package com.example.routerservice.domain

import java.time.Instant
import java.util.UUID

data class CardRequestDto(
    val id: UUID,
    val cardId: String,
    val clientId: String,
    val type: CardRequestType,
    val status: CardRequestStatus,
    val createdAt: Instant,
    val updatedAt: Instant
)
