package com.coinbattle.domain.order.service

import com.coinbattle.domain.market.repository.TickerRedisRepository
import com.coinbattle.domain.order.dto.LiquidationNotificationMessage
import com.coinbattle.domain.order.entity.OrderDirection
import com.coinbattle.domain.order.entity.PositionStatus
import com.coinbattle.domain.order.repository.PositionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import java.math.BigDecimal
import java.time.LocalDateTime

@Component
class LiquidationScheduler(
    private val positionRepository: PositionRepository,
    private val tickerRedisRepository: TickerRedisRepository,
    private val orderService: OrderService,
    private val messagingTemplate: SimpMessagingTemplate
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @PostConstruct
    fun start() {
        scope.launch {
            while (isActive) {
                runCatching { checkLiquidations() }
                    .onFailure { log.error("liquidation check error", it) }
                delay(1_000L)
            }
        }
    }

    @PreDestroy
    fun stop() {
        scope.cancel()
    }

    private fun checkLiquidations() {
        val openPositions = positionRepository.findAllByStatus(PositionStatus.OPEN)
        for (position in openPositions) {
            val currentPrice = tickerRedisRepository.findByMarket(position.ticker)
                ?.tradePrice?.toLong() ?: continue
            val liquidationPrice = position.liquidationPrice()
            val shouldLiquidate = when (position.direction) {
                OrderDirection.LONG -> currentPrice <= liquidationPrice
                OrderDirection.SHORT -> currentPrice >= liquidationPrice
            }
            if (shouldLiquidate) {
                runCatching {
                    val result = orderService.forceClose(position.id, liquidationPrice)
                    log.warn(
                        "liquidated positionId={} userId={} ticker={} price={}",
                        position.id, position.userId, position.ticker, liquidationPrice
                    )
                    val lossAmount = result.realizedPnl
                        ?.let { BigDecimal.valueOf(-it).coerceAtLeast(BigDecimal.ZERO) }
                        ?: BigDecimal.ZERO
                    messagingTemplate.convertAndSendToUser(
                        position.userId.toString(),
                        "/queue/notification",
                        LiquidationNotificationMessage(
                            ticker = position.ticker,
                            positionType = position.direction.name,
                            lossAmount = lossAmount,
                            liquidatedAt = LocalDateTime.now()
                        )
                    )
                }.onFailure {
                    log.error("forceClose failed positionId={}", position.id, it)
                }
            }
        }
    }
}
