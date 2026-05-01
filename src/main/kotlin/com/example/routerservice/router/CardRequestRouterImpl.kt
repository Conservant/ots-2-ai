package com.example.routerservice.router

import com.example.routerservice.domain.CardRequest
import com.example.routerservice.domain.CardRequestType
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.jms.core.JmsTemplate
import org.springframework.stereotype.Component

@Component
class CardRequestRouterImpl(
    private val jmsTemplate: JmsTemplate,
    private val objectMapper: ObjectMapper
) : CardRequestRouter {

    override fun route(cardRequest: CardRequest) {
        val destination = when (cardRequest.type) {
            CardRequestType.OPEN    -> "card.open"
            CardRequestType.CLOSE   -> "card.close"
            CardRequestType.BLOCK   -> "card.block"
            CardRequestType.UNBLOCK -> "card.unblock"
        }
        val payload = objectMapper.writeValueAsString(cardRequest)
        jmsTemplate.convertAndSend(destination, payload)
    }
}
