package com.example.routerservice.repository

import com.example.routerservice.domain.OutboxEvent
import com.example.routerservice.domain.OutboxStatus
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

internal interface OutboxEventRepository : JpaRepository<OutboxEvent, Long> {

    fun existsByEventId(eventId: UUID): Boolean

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        value = """
            SELECT * FROM outbox_event oe
            WHERE oe.status IN ('NEW', 'RETRY')
              AND oe.next_attempt_at <= :now
            ORDER BY oe.created_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
        """,
        nativeQuery = true,
    )
    fun lockBatchForDispatch(
        @Param("now") now: Instant,
        @Param("limit") limit: Int,
    ): List<OutboxEvent>

    fun findAllByStatusAndLockedAtBefore(
        status: OutboxStatus,
        lockedAt: Instant,
    ): List<OutboxEvent>
}
