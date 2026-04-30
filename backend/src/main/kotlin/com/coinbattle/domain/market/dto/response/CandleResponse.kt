package com.coinbattle.domain.market.dto.response

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class CandleResponse(
    val market: String,
    val candleDateTimeUtc: String,
    val candleDateTimeKst: String,
    val openingPrice: Double,
    val highPrice: Double,
    val lowPrice: Double,
    val tradePrice: Double,
    val candleAccTradeVolume: Double,
    val timestamp: Long
)

data class CandleListResponse(
    val market: String,
    val unit: Int,
    val candles: List<CandleResponse>,
    val totalCount: Int
)

enum class CandleUnit(val minutes: Int) {
    ONE(1), THREE(3), FIVE(5), TEN(10),
    FIFTEEN(15), THIRTY(30), SIXTY(60), TWO_FORTY(240);

    companion object {
        fun from(value: Int): CandleUnit =
            entries.find { it.minutes == value }
                ?: throw IllegalArgumentException("Invalid candle unit: $value")
    }
}
