package com.coinbattle.domain.order.dto.response

import com.coinbattle.domain.order.entity.Order
import com.coinbattle.domain.order.entity.OrderDirection
import com.coinbattle.domain.order.entity.OrderSide
import com.coinbattle.domain.order.entity.OrderStatus
import com.coinbattle.domain.order.entity.OrderType
import java.math.BigDecimal
import java.time.LocalDateTime

data class OrderResponse(
    val id: Long,
    val ticker: String,
    val orderType: OrderType,
    val direction: OrderDirection,
    val side: OrderSide,
    val requestedAmount: Long?,
    val executedPrice: Long?,
    val marketPrice: Long? = null,
    val slippageRate: BigDecimal? = null,
    val executedAmount: Long?,
    val executedQuantity: BigDecimal?,
    val leverage: Int,
    val realizedPnl: Long?,
    val status: OrderStatus,
    val createdAt: LocalDateTime
) {
    companion object {
        fun from(order: Order) = OrderResponse(
            id = order.id,
            ticker = order.ticker,
            orderType = order.orderType,
            direction = order.direction,
            side = order.side,
            requestedAmount = order.requestedAmount,
            executedPrice = order.executedPrice,
            executedAmount = order.executedAmount,
            executedQuantity = order.executedQuantity,
            leverage = order.leverage,
            realizedPnl = order.realizedPnl,
            status = order.status,
            createdAt = order.createdAt
        )
    }
}
