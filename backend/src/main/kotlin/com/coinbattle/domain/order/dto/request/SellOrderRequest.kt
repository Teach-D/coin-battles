package com.coinbattle.domain.order.dto.request

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal

data class SellOrderRequest(
    @field:NotBlank val idempotencyKey: String,
    @field:NotNull val positionId: Long,
    @field:DecimalMin(value = "0.0", inclusive = false)
    @field:DecimalMax(value = "1.0") val closeRatio: BigDecimal
)
