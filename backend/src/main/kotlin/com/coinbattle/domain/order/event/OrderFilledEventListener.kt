package com.coinbattle.domain.order.event

import com.coinbattle.domain.ranking.service.RankingService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class OrderFilledEventListener(
    private val rankingService: RankingService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async("rankingExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onOrderFilled(event: OrderFilledEvent) {
        rankingService.updateRanking(event.userId, event.evaluatedValue)
        log.info("order.filled userId={} orderId={} evaluatedValue={}", event.userId, event.orderId, event.evaluatedValue)
    }
}
