package com.coinbattle.domain.market.dto.response

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.Instant

@JsonInclude(JsonInclude.Include.NON_NULL)
data class TickerResponse(
    val market: String,
    val tradePrice: Double,
    val changeRate: Double,
    val changePrice: Double,
    val change: ChangeType,
    val accTradeVolume24h: Double,
    val accTradePrice24h: Double,
    val highPrice: Double,
    val lowPrice: Double,
    val cachedAt: Instant? = null,
    val timestamp: Long? = null
)
