package com.example.routerservice.repository

import com.example.routerservice.domain.CardRequest
import com.example.routerservice.domain.CardRequestStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CardRequestRepository : JpaRepository<CardRequest, UUID> {
    fun findAllByStatusOrderByUpdatedAtAsc(status: CardRequestStatus, pageable: Pageable): List<CardRequest>
}
