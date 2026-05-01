package com.example.routerservice.controller

import com.example.routerservice.domain.CardRequestDto
import com.example.routerservice.domain.CardRequestStatus
import com.example.routerservice.domain.CardRequestType
import com.example.routerservice.service.CardRequestNotFoundException
import com.example.routerservice.service.CardRequestService
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.util.UUID

class CardRequestControllerTest : DescribeSpec({

    val service = mock(CardRequestService::class.java)
    val controller = CardRequestController(service)
    val mockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(controller)
        .setControllerAdvice(GlobalExceptionHandler())
        .build()

    fun sampleDto(id: UUID = UUID.randomUUID()) = CardRequestDto(
        id = id,
        cardId = "CARD-001",
        clientId = "CLIENT-42",
        type = CardRequestType.BLOCK,
        status = CardRequestStatus.ROUTED,
        createdAt = Instant.parse("2024-01-15T10:30:00Z"),
        updatedAt = Instant.parse("2024-01-15T10:30:01Z")
    )

    describe("GET /api/card-requests/{id}") {

        it("returns 200 OK with DTO when card request exists") {
            val id = UUID.randomUUID()
            val dto = sampleDto(id)
            `when`(service.getById(id)).thenReturn(dto)

            mockMvc.perform(get("/api/card-requests/$id"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.cardId").value("CARD-001"))
                .andExpect(jsonPath("$.clientId").value("CLIENT-42"))
                .andExpect(jsonPath("$.type").value("BLOCK"))
                .andExpect(jsonPath("$.status").value("ROUTED"))
        }

        it("returns 404 Not Found when card request does not exist") {
            val id = UUID.randomUUID()
            `when`(service.getById(id)).thenThrow(CardRequestNotFoundException(id))

            mockMvc.perform(get("/api/card-requests/$id"))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").isNotEmpty)
        }

        it("returns 400 Bad Request when id is not a valid UUID") {
            mockMvc.perform(get("/api/card-requests/not-a-uuid"))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").isNotEmpty)
        }
    }
})
