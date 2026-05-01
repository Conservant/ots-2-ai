package com.example.routerservice.service

import java.util.UUID

class CardRequestNotFoundException(id: UUID) :
    RuntimeException("Card request not found: $id")
