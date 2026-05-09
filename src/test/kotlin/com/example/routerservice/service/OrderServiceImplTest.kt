package com.example.routerservice.service

import com.example.routerservice.domain.CreateOrderCommand
import com.example.routerservice.domain.OrderEntity
import com.example.routerservice.domain.OrderStatus
import com.example.routerservice.domain.OutboxEvent
import com.example.routerservice.mapper.toDto
import com.example.routerservice.repository.OrderRepository
import com.example.routerservice.repository.OutboxEventRepository
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.any
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.Instant
import java.util.UUID

class OrderServiceImplTest : DescribeSpec({
    describe("createOrder()") {
        it("Given valid command When creating order Then persists order and outbox event atomically") {
            val orderRepository = org.mockito.Mockito.mock(OrderRepository::class.java)
            val outboxRepository = org.mockito.Mockito.mock(OutboxEventRepository::class.java)
            val service = OrderServiceImpl(orderRepository, outboxRepository, ObjectMapper())
            val command = CreateOrderCommand(status = OrderStatus.CREATED)

            `when`(orderRepository.save(any(OrderEntity::class.java))).thenReturn(
                OrderEntity(
                    id = 101L,
                    status = OrderStatus.CREATED,
                    createdAt = Instant.now(),
                ),
            )
            `when`(outboxRepository.save(any(OutboxEvent::class.java))).thenAnswer { it.arguments[0] }

            val result = service.createOrder(command)

            val outboxCaptor = ArgumentCaptor.forClass(OutboxEvent::class.java)
            verify(orderRepository, times(1)).save(any(OrderEntity::class.java))
            verify(outboxRepository, times(1)).save(outboxCaptor.capture())
            result.id shouldBe 101L
            result.status shouldBe OrderStatus.CREATED
            outboxCaptor.value.aggregateId shouldBe result.id.toString()
            outboxCaptor.value.status.name shouldBe "NEW"
        }

        it("Given persisted order When mapping to dto Then returns stable response") {
            val order = OrderEntity(
                id = 200L,
                status = OrderStatus.CONFIRMED,
                createdAt = Instant.now(),
            )

            val dto = order.toDto()

            dto.id shouldBe 200L
            dto.status shouldBe OrderStatus.CONFIRMED
        }
    }
})
