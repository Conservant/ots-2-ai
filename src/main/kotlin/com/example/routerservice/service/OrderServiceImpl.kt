package com.example.routerservice.service

import com.example.routerservice.domain.CreateOrderCommand
import com.example.routerservice.domain.OrderDto
import com.example.routerservice.mapper.toCreatedOutboxEvent
import com.example.routerservice.mapper.toDto
import com.example.routerservice.mapper.toEntity
import com.example.routerservice.repository.OrderRepository
import com.example.routerservice.repository.OutboxEventRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
internal class OrderServiceImpl(
    private val orderRepository: OrderRepository,
    private val outboxEventRepository: OutboxEventRepository,
    private val objectMapper: ObjectMapper,
) : OrderService {

    @Transactional
    override fun createOrder(command: CreateOrderCommand): OrderDto {
        val now = Instant.now()
        val savedOrder = orderRepository.save(command.toEntity())
        val payload = objectMapper.writeValueAsString(savedOrder.toDto())
        val outboxEvent = savedOrder.toCreatedOutboxEvent(
            eventId = UUID.randomUUID(),
            payload = payload,
            now = now,
        )
        outboxEventRepository.save(outboxEvent)
        return savedOrder.toDto()
    }
}
