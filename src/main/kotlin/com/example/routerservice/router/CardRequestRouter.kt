package com.example.routerservice.router

import com.example.routerservice.domain.CardRequest

interface CardRequestRouter {
    fun route(cardRequest: CardRequest)
}
