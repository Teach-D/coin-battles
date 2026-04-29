package com.coinbattle.domain.order.event

data class OrderFilledEvent(
    val orderId: Long,
    val userId: Long,
    val ticker: String,
    val evaluatedValue: Long
)
