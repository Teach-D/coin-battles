package com.coinbattle.domain.order.event

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class OrderFilledEventListener(
    private val stringRedisTemplate: StringRedisTemplate
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async("rankingExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onOrderFilled(event: OrderFilledEvent) {
        updateRanking(event.userId, event.evaluatedValue)
        log.info("order.filled userId={} orderId={} evaluatedValue={}", event.userId, event.orderId, event.evaluatedValue)
    }

    private fun updateRanking(userId: Long, evaluatedValue: Long) {
        val score = evaluatedValue.toDouble()
        stringRedisTemplate.opsForZSet().add("leaderboard:season", userId.toString(), score)
        stringRedisTemplate.opsForZSet().add("leaderboard:daily", userId.toString(), score)
    }
}
