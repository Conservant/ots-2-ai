package com.example.routerservice.router

import com.example.routerservice.domain.CardRequest
import com.example.routerservice.domain.CardRequestStatus
import com.example.routerservice.domain.CardRequestType
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.enum
import io.kotest.property.checkAll
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.springframework.jms.core.JmsTemplate
import java.time.Instant
import java.util.UUID

class CardRequestRouterImplTest : DescribeSpec({

    val objectMapper = ObjectMapper().registerKotlinModule()

    fun cardRequest(type: CardRequestType) = CardRequest(
        id = UUID.randomUUID(),
        requestId = UUID.randomUUID(),
        cardId = "CARD-001",
        clientId = "CLIENT-42",
        type = type,
        status = CardRequestStatus.RECEIVED,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    fun freshRouter(): Pair<JmsTemplate, CardRequestRouterImpl> {
        val jms = mock(JmsTemplate::class.java)
        return jms to CardRequestRouterImpl(jms, objectMapper)
    }

    describe("CardRequestRouterImpl") {

        it("routes OPEN to card.open") {
            val (jms, router) = freshRouter()
            router.route(cardRequest(CardRequestType.OPEN))
            val captor = ArgumentCaptor.forClass(String::class.java)
            verify(jms).convertAndSend(captor.capture(), captor.capture())
            captor.allValues[0] shouldBe "card.open"
        }

        it("routes CLOSE to card.close") {
            val (jms, router) = freshRouter()
            router.route(cardRequest(CardRequestType.CLOSE))
            val captor = ArgumentCaptor.forClass(String::class.java)
            verify(jms).convertAndSend(captor.capture(), captor.capture())
            captor.allValues[0] shouldBe "card.close"
        }

        it("routes BLOCK to card.block") {
            val (jms, router) = freshRouter()
            router.route(cardRequest(CardRequestType.BLOCK))
            val captor = ArgumentCaptor.forClass(String::class.java)
            verify(jms).convertAndSend(captor.capture(), captor.capture())
            captor.allValues[0] shouldBe "card.block"
        }

        it("routes UNBLOCK to card.unblock") {
            val (jms, router) = freshRouter()
            router.route(cardRequest(CardRequestType.UNBLOCK))
            val captor = ArgumentCaptor.forClass(String::class.java)
            verify(jms).convertAndSend(captor.capture(), captor.capture())
            captor.allValues[0] shouldBe "card.unblock"
        }

        it("sends to exactly one queue per route() call") {
            val (jms, router) = freshRouter()
            router.route(cardRequest(CardRequestType.OPEN))
            verify(jms).convertAndSend(ArgumentCaptor.forClass(String::class.java).capture(),
                ArgumentCaptor.forClass(String::class.java).capture())
            verifyNoMoreInteractions(jms)
        }

        describe("property: for any valid CardRequestType, route() dispatches to exactly one correct queue") {
            it("holds for all enum values") {
                val expectedQueues = mapOf(
                    CardRequestType.OPEN    to "card.open",
                    CardRequestType.CLOSE   to "card.close",
                    CardRequestType.BLOCK   to "card.block",
                    CardRequestType.UNBLOCK to "card.unblock"
                )
                checkAll(Arb.enum<CardRequestType>()) { type ->
                    val (jms, router) = freshRouter()
                    router.route(cardRequest(type))
                    val captor = ArgumentCaptor.forClass(String::class.java)
                    verify(jms).convertAndSend(captor.capture(), captor.capture())
                    captor.allValues[0] shouldBe expectedQueues[type]
                    verifyNoMoreInteractions(jms)
                }
            }
        }
    }
})
