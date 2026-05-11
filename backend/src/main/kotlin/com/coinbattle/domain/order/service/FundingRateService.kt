package com.coinbattle.domain.order.service

import com.coinbattle.domain.market.repository.TickerRedisRepository
import com.coinbattle.domain.order.entity.OrderDirection
import com.coinbattle.domain.order.entity.PositionStatus
import com.coinbattle.domain.order.repository.PositionRepository
import com.coinbattle.domain.user.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class FundingRateService(
    private val positionRepository: PositionRepository,
    private val tickerRedisRepository: TickerRedisRepository,
    private val userRepository: UserRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val fundingRate = BigDecimal("0.0001")

    fun settleAll() {
        val openPositions = positionRepository.findAllByStatus(PositionStatus.OPEN)
        for (position in openPositions) {
            runCatching { settle(position.id) }
                .onFailure { log.error("funding settlement failed positionId={}", position.id, it) }
        }
    }

    private fun settle(positionId: Long) {
        val position = positionRepository.findById(positionId).orElse(null) ?: return
        if (position.status != PositionStatus.OPEN) return

        val currentPrice = tickerRedisRepository.findByMarket(position.ticker)?.tradePrice?.toLong()
        if (currentPrice == null) {
            log.warn("funding skip: no price positionId={} ticker={}", positionId, position.ticker)
            return
        }

        val positionValue = position.quantity.multiply(BigDecimal(currentPrice))
        val fundingFee = positionValue.multiply(fundingRate).toLong()

        val user = userRepository.findById(position.userId).orElse(null) ?: return
        when (position.direction) {
            OrderDirection.LONG -> user.balance = (user.balance - fundingFee).coerceAtLeast(0L)
            OrderDirection.SHORT -> user.balance += fundingFee
        }
        userRepository.save(user)

        log.info(
            "funding settled positionId={} userId={} direction={} fee={}",
            positionId, position.userId, position.direction, fundingFee
        )
    }
}
