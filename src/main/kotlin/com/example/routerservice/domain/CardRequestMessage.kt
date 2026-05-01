package com.example.routerservice.domain

import java.time.Instant

data class CardRequestMessage(
    val cardId: String,
    val clientId: String,
    val type: CardRequestType,
    val requestedAt: Instant
)
