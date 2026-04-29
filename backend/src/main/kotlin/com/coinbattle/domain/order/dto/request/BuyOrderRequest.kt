package com.coinbattle.domain.order.dto.request

import com.coinbattle.domain.order.entity.OrderDirection
import com.coinbattle.domain.order.entity.OrderType
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class BuyOrderRequest(
    @field:NotBlank val idempotencyKey: String,
    @field:NotBlank val ticker: String,
    @field:NotNull val orderType: OrderType,
    @field:NotNull val direction: OrderDirection,
    @field:Min(1000) val amount: Long,
    @field:Min(1) @field:Max(10) val leverage: Int,
    val limitPrice: Long? = null
)
