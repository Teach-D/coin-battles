package com.coinbattle.domain.order.dto

import java.math.BigDecimal
import java.time.LocalDateTime

data class LiquidationNotificationMessage(
    val type: String = "LIQUIDATED",
    val ticker: String,
    val positionType: String,
    val lossAmount: BigDecimal,
    val liquidatedAt: LocalDateTime
)
