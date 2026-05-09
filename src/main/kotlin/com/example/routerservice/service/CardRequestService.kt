package com.example.routerservice.service

import com.example.routerservice.domain.CardRequestDto
import com.example.routerservice.domain.CardRequestMessage
import java.util.UUID

interface CardRequestService {
    fun process(message: CardRequestMessage)
    fun retryFailed(batchSize: Int): Int
    fun getById(id: UUID): CardRequestDto
}
