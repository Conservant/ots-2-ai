package com.example.routerservice.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "orders")
data class OrderEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: OrderStatus,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
)
