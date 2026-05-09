CREATE TABLE shedlock (
name       VARCHAR(64)  NOT NULL,
lock_until TIMESTAMPTZ  NOT NULL,
locked_at  TIMESTAMPTZ  NOT NULL,
locked_by  VARCHAR(255) NOT NULL,
CONSTRAINT pk_shedlock PRIMARY KEY (name)
);



package com.example.routerservice.scheduler

import com.example.routerservice.service.CardRequestService
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class CardRequestRetryScheduler(
private val cardRequestService: CardRequestService,
@Value("\${app.card-request-retry.batch-size:100}")
private val batchSize: Int,
) {

    private val log = LoggerFactory.getLogger(CardRequestRetryScheduler::class.java)

    @Scheduled(cron = "\${app.card-request-retry.cron:0 */1 * * * *}")
    @SchedulerLock(
        name = "cardRequestRetryScheduler_retryFailed",
        lockAtMostFor = "\${app.card-request-retry.lock-at-most-for:PT5M}",
        lockAtLeastFor = "\${app.card-request-retry.lock-at-least-for:PT10S}",
    )
    fun retryFailed() {
        val retryCount = cardRequestService.retryFailed(batchSize)

        if (retryCount > 0) {
            log.info("Retried {} failed card requests", retryCount)
        }
    }
}


interface CardRequestRepository : JpaRepository<CardRequest, UUID> {
fun findAllByStatusOrderByUpdatedAtAsc(status: CardRequestStatus, pageable: Pageable): List<CardRequest>
}
