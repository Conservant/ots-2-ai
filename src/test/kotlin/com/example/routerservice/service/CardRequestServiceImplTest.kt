package com.example.routerservice.service

import com.example.routerservice.domain.CardRequest
import com.example.routerservice.domain.CardRequestMessage
import com.example.routerservice.domain.CardRequestStatus
import com.example.routerservice.domain.CardRequestType
import com.example.routerservice.repository.CardRequestRepository
import com.example.routerservice.router.CardRequestRouter
import com.example.routerservice.router.UnknownCardRequestTypeException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import java.time.Instant
import java.util.Optional
import java.util.UUID

class CardRequestServiceImplTest : DescribeSpec({

    fun message(
        cardId: String = "CARD-001",
        clientId: String = "CLIENT-42",
        type: CardRequestType = CardRequestType.OPEN
    ) = CardRequestMessage(cardId = cardId, clientId = clientId, type = type, requestedAt = Instant.now())

    fun savedEntity(id: UUID, msg: CardRequestMessage, status: CardRequestStatus = CardRequestStatus.RECEIVED): CardRequest =
        CardRequest(
            id = id,
            requestId = id,
            cardId = msg.cardId,
            clientId = msg.clientId,
            type = msg.type,
            status = status,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

    fun setup(): Triple<CardRequestRepository, CardRequestRouter, CardRequestServiceImpl> {
        val repo = mock(CardRequestRepository::class.java)
        val router = mock(CardRequestRouter::class.java)
        val service = CardRequestServiceImpl(repo, router)
        return Triple(repo, router, service)
    }

    describe("process()") {

        it("saves entity with status RECEIVED before routing") {
            val (repo, router, service) = setup()
            val msg = message()
            val captor = ArgumentCaptor.forClass(CardRequest::class.java)

            `when`(repo.save(any(CardRequest::class.java))).thenAnswer { it.arguments[0] }

            service.process(msg)

            verify(repo, atLeastOnce()).save(captor.capture())
            val firstSave = captor.allValues.first()
            firstSave.status shouldBe CardRequestStatus.RECEIVED
            firstSave.cardId shouldBe msg.cardId
            firstSave.clientId shouldBe msg.clientId
            firstSave.type shouldBe msg.type
        }

        it("generates a non-null UUID id in the service layer") {
            val (repo, _, service) = setup()
            val msg = message()
            val captor = ArgumentCaptor.forClass(CardRequest::class.java)

            `when`(repo.save(any(CardRequest::class.java))).thenAnswer { it.arguments[0] }

            service.process(msg)

            verify(repo, atLeastOnce()).save(captor.capture())
            captor.allValues.first().id shouldNotBe null
        }

        it("calls router after saving") {
            val (repo, router, service) = setup()
            val msg = message()

            `when`(repo.save(any(CardRequest::class.java))).thenAnswer { it.arguments[0] }

            service.process(msg)

            val inOrder = inOrder(repo, router)
            inOrder.verify(repo).save(any(CardRequest::class.java))
            inOrder.verify(router).route(any(CardRequest::class.java))
        }

        it("updates status to ROUTED on success") {
            val (repo, _, service) = setup()
            val msg = message()
            val captor = ArgumentCaptor.forClass(CardRequest::class.java)

            `when`(repo.save(any(CardRequest::class.java))).thenAnswer { it.arguments[0] }

            service.process(msg)

            verify(repo, times(2)).save(captor.capture())
            captor.allValues.last().status shouldBe CardRequestStatus.ROUTED
        }

        it("updates status to FAILED when routing throws") {
            val (repo, router, service) = setup()
            val msg = message()
            val captor = ArgumentCaptor.forClass(CardRequest::class.java)

            `when`(repo.save(any(CardRequest::class.java))).thenAnswer { it.arguments[0] }
            doThrow(UnknownCardRequestTypeException(msg.type)).`when`(router).route(any(CardRequest::class.java))

            service.process(msg)

            verify(repo, times(2)).save(captor.capture())
            captor.allValues.last().status shouldBe CardRequestStatus.FAILED
        }

        it("does not route when DB save throws") {
            val (repo, router, service) = setup()
            val msg = message()

            `when`(repo.save(any(CardRequest::class.java))).thenThrow(RuntimeException("DB error"))

            shouldThrow<RuntimeException> { service.process(msg) }

            verifyNoInteractions(router)
        }

        describe("property: for any non-blank cardId and clientId, process() persists an entity with matching fields") {
            it("holds for arbitrary string inputs") {
                checkAll(
                    Arb.string(minSize = 1, maxSize = 50),
                    Arb.string(minSize = 1, maxSize = 50)
                ) { cardId, clientId ->
                    val (repo, _, service) = setup()
                    val msg = message(cardId = cardId, clientId = clientId)
                    val captor = ArgumentCaptor.forClass(CardRequest::class.java)

                    `when`(repo.save(any(CardRequest::class.java))).thenAnswer { it.arguments[0] }

                    service.process(msg)

                    verify(repo, atLeastOnce()).save(captor.capture())
                    val first = captor.allValues.first()
                    first.cardId shouldBe cardId
                    first.clientId shouldBe clientId
                }
            }
        }
    }

    describe("getById()") {

        it("returns DTO when entity exists") {
            val (repo, _, service) = setup()
            val id = UUID.randomUUID()
            val msg = message()
            val entity = savedEntity(id, msg, CardRequestStatus.ROUTED)

            `when`(repo.findById(id)).thenReturn(Optional.of(entity))

            val dto = service.getById(id)

            dto.id shouldBe id
            dto.cardId shouldBe msg.cardId
            dto.clientId shouldBe msg.clientId
            dto.type shouldBe msg.type
            dto.status shouldBe CardRequestStatus.ROUTED
        }

        it("throws CardRequestNotFoundException when entity does not exist") {
            val (repo, _, service) = setup()
            val id = UUID.randomUUID()

            `when`(repo.findById(id)).thenReturn(Optional.empty())

            shouldThrow<CardRequestNotFoundException> { service.getById(id) }
        }
    }
})
