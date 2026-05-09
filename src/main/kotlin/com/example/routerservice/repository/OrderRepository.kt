package com.example.routerservice.repository

import com.example.routerservice.domain.OrderEntity
import org.springframework.data.jpa.repository.JpaRepository

internal interface OrderRepository : JpaRepository<OrderEntity, Long>
