package com.example.routerservice.controller

import com.example.routerservice.domain.CardRequestDto
import com.example.routerservice.service.CardRequestService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/card-requests")
class CardRequestController(private val service: CardRequestService) {

    @GetMapping("/{id}")
    fun getById(@PathVariable id: UUID): ResponseEntity<CardRequestDto> {
        val dto = service.getById(id)
        return ResponseEntity.ok(dto)
    }
}
