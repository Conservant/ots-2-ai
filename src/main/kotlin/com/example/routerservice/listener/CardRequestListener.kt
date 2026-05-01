package com.example.routerservice.listener

import com.example.routerservice.domain.CardRequestMessage
import com.example.routerservice.service.CardRequestService
import org.slf4j.LoggerFactory
import org.springframework.jms.annotation.JmsListener
import org.springframework.stereotype.Component

@Component
class CardRequestListener(private val service: CardRequestService) {

    private val log = LoggerFactory.getLogger(CardRequestListener::class.java)

    @JmsListener(destination = "card.requests")
    fun onMessage(message: CardRequestMessage) {
        log.info("Received card request: cardId={}, type={}", message.cardId, message.type)
        service.process(message)
    }
}
