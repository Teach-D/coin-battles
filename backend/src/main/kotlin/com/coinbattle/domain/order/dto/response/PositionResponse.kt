package com.coinbattle.domain.order.dto.response

import com.coinbattle.domain.order.entity.OrderDirection
import com.coinbattle.domain.order.entity.Position
import com.coinbattle.domain.order.entity.PositionStatus
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime

data class PositionResponse(
    val positionId: Long,
    val ticker: String,
    val direction: OrderDirection,
    val quantity: BigDecimal,
    val averagePrice: Long,
    val leverage: Int,
    val margin: Long,
    val liquidationPrice: Long,
    val currentPrice: Long,
    val unrealizedPnl: Long,
    val evaluatedValue: Long,
    val unrealizedPnlRate: BigDecimal,
    val status: PositionStatus,
    val openedAt: LocalDateTime
) {
    companion object {
        private val SCALE = 10
        private val ROUNDING = RoundingMode.HALF_UP

        fun from(position: Position, currentPrice: Long): PositionResponse {
            val unrealizedPnl = position.unrealizedPnl(currentPrice)
            val evaluatedValue = position.evaluatedValue(currentPrice)
            val unrealizedPnlRate = if (position.margin == 0L) {
                BigDecimal.ZERO
            } else {
                BigDecimal(unrealizedPnl)
                    .divide(BigDecimal(position.margin), SCALE, ROUNDING)
                    .multiply(BigDecimal("100"))
                    .setScale(SCALE, ROUNDING)
            }

            return PositionResponse(
                positionId = position.id,
                ticker = position.ticker,
                direction = position.direction,
                quantity = position.quantity,
                averagePrice = position.averagePrice,
                leverage = position.leverage,
                margin = position.margin,
                liquidationPrice = position.liquidationPrice(),
                currentPrice = currentPrice,
                unrealizedPnl = unrealizedPnl,
                evaluatedValue = evaluatedValue,
                unrealizedPnlRate = unrealizedPnlRate,
                status = position.status,
                openedAt = position.openedAt
            )
        }
    }
}
