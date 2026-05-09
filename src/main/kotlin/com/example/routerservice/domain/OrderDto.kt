package com.example.routerservice.domain

import java.time.Instant

internal data class OrderDto(
    val id: Long,
    val status: OrderStatus,
    val createdAt: Instant,
)
