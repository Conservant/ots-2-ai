package com.example.routerservice.repository

import com.example.routerservice.domain.CardRequest
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CardRequestRepository : JpaRepository<CardRequest, UUID>
