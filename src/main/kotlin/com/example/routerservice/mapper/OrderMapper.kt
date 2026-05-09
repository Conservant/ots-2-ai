package com.example.routerservice.mapper

import com.example.routerservice.domain.CreateOrderCommand
import com.example.routerservice.domain.OrderDto
import com.example.routerservice.domain.OrderEntity

internal fun CreateOrderCommand.toEntity(): OrderEntity = OrderEntity(
    status = status,
)

internal fun OrderEntity.toDto(): OrderDto = OrderDto(
    id = requireNotNull(id) { "Order id must be present before mapping to dto" },
    status = status,
    createdAt = createdAt,
)
