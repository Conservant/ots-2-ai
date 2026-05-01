package com.example.routerservice.router

import com.example.routerservice.domain.CardRequestType

class UnknownCardRequestTypeException(type: CardRequestType) :
    RuntimeException("Unknown card request type: $type")
