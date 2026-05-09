package com.example.routerservice.service

import com.example.routerservice.domain.CardRequest
import com.example.routerservice.domain.CardRequestDto
import com.example.routerservice.domain.CardRequestMessage
import com.example.routerservice.domain.CardRequestStatus
import com.example.routerservice.repository.CardRequestRepository
import com.example.routerservice.router.CardRequestRouter
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class CardRequestServiceImpl(
    private val repository: CardRequestRepository,
    private val router: CardRequestRouter,
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
            updateStatus(saved, CardRequestStatus.ROUTED)
        } catch (e: Exception) {
            log.error("Routing failed for card request ${saved.id}", e)
            updateStatus(saved, CardRequestStatus.FAILED)
        }
    }

    override fun retryFailed(batchSize: Int): Int {
        val failedRequests = repository.findAllByStatusOrderByUpdatedAtAsc(
            status = CardRequestStatus.FAILED,
            pageable = PageRequest.of(0, batchSize),
        )

        failedRequests.forEach { failedRequest ->
            try {
                router.route(failedRequest)
                updateStatus(failedRequest, CardRequestStatus.ROUTED)
            } catch (e: Exception) {
                log.error("Retry failed for card request ${failedRequest.id}", e)
                updateStatus(failedRequest, CardRequestStatus.FAILED)
            }
        }

        return failedRequests.size
    }

    override fun getById(id: UUID): CardRequestDto {
        val entity = repository.findById(id)
            .orElseThrow { CardRequestNotFoundException(id) }
        return entity.toDto()
    }

    private fun updateStatus(cardRequest: CardRequest, status: CardRequestStatus) {
        cardRequest.status = status
        cardRequest.updatedAt = Instant.now()
        repository.save(cardRequest)
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
