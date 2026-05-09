package com.example.routerservice.domain

internal data class CreateOrderCommand(
    val status: OrderStatus = OrderStatus.CREATED,
)
