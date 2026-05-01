package com.example.routerservice.service

import com.example.routerservice.domain.CardRequest
import com.example.routerservice.domain.CardRequestDto
import com.example.routerservice.domain.CardRequestMessage
import com.example.routerservice.domain.CardRequestStatus
import com.example.routerservice.repository.CardRequestRepository
import com.example.routerservice.router.CardRequestRouter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class CardRequestServiceImpl(
    private val repository: CardRequestRepository,
    private val router: CardRequestRouter
) : CardRequestService {

    private val log = LoggerFactory.getLogger(CardRequestServiceImpl::class.java)

    override fun process(message: CardRequestMessage) {
        val id = UUID.randomUUID()

        val entity = CardRequest(
            id = id,
            requestId = id,
            cardId = message.cardId,
            clientId = message.clientId,
            type = message.type,
            status = CardRequestStatus.RECEIVED,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        val saved = repository.save(entity)

        try {
            router.route(saved)
            saved.status = CardRequestStatus.ROUTED
            saved.updatedAt = Instant.now()
            repository.save(saved)
        } catch (e: Exception) {
            log.error("Routing failed for card request ${saved.id}", e)
            saved.status = CardRequestStatus.FAILED
            saved.updatedAt = Instant.now()
            repository.save(saved)
        }
    }

    override fun getById(id: UUID): CardRequestDto {
        val entity = repository.findById(id)
            .orElseThrow { CardRequestNotFoundException(id) }
        return entity.toDto()
    }

    private fun CardRequest.toDto() = CardRequestDto(
        id = id,
        cardId = cardId,
        clientId = clientId,
        type = type,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
