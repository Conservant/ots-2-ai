package com.example.routerservice.repository

import com.example.routerservice.domain.CardRequest
import com.example.routerservice.domain.CardRequestStatus
import com.example.routerservice.domain.CardRequestType
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.optional.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.UUID

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class CardRequestRepositoryIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:15-alpine")

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }

    @Autowired
    lateinit var repository: CardRequestRepository

    private fun newCardRequest(
        status: CardRequestStatus = CardRequestStatus.RECEIVED,
        type: CardRequestType = CardRequestType.OPEN
    ) = CardRequest(
        id = UUID.randomUUID(),
        requestId = UUID.randomUUID(),
        cardId = "card-${UUID.randomUUID()}",
        clientId = "client-${UUID.randomUUID()}",
        type = type,
        status = status,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    @Test
    fun `save and findById returns the saved entity`() {
        val entity = newCardRequest()
        repository.save(entity)

        val found = repository.findById(entity.id)

        found shouldNotBe null
        found.get().id shouldBe entity.id
        found.get().cardId shouldBe entity.cardId
        found.get().clientId shouldBe entity.clientId
        found.get().type shouldBe entity.type
        found.get().status shouldBe entity.status
    }

    @Test
    fun `findById returns empty when not found`() {
        val result = repository.findById(UUID.randomUUID())
        result.shouldBeEmpty()
    }

    @Test
    fun `save updates status`() {
        val entity = newCardRequest(status = CardRequestStatus.RECEIVED)
        repository.save(entity)

        entity.status = CardRequestStatus.ROUTED
        entity.updatedAt = Instant.now()
        repository.save(entity)

        val found = repository.findById(entity.id)
        found.get().status shouldBe CardRequestStatus.ROUTED
    }

    @Test
    fun `findAll returns all saved entities`() {
        val first = newCardRequest()
        val second = newCardRequest(type = CardRequestType.BLOCK)
        repository.save(first)
        repository.save(second)

        val all = repository.findAll().filter { it.id == first.id || it.id == second.id }

        all shouldHaveSize 2
    }
}
