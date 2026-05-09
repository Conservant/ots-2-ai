package com.example.routerservice.service

import com.example.routerservice.domain.CreateOrderCommand
import com.example.routerservice.domain.OrderDto

internal interface OrderService {
    fun createOrder(command: CreateOrderCommand): OrderDto
}
