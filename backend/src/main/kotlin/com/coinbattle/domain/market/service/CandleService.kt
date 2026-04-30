package com.coinbattle.domain.market.service

import com.coinbattle.common.exception.CoinBattleException
import com.coinbattle.common.exception.ErrorCode
import com.coinbattle.domain.market.dto.response.CandleListResponse
import com.coinbattle.domain.market.dto.response.CandleResponse
import com.coinbattle.domain.market.dto.response.CandleUnit
import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.coroutines.delay
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

@Service
class CandleService(
    private val webClient: WebClient
) {
    suspend fun getCandles(market: String, unit: Int, count: Int, pages: Int = 1): CandleListResponse {
        val candleUnit = runCatching { CandleUnit.from(unit) }
            .getOrElse { throw CoinBattleException(ErrorCode.INVALID_CANDLE_UNIT) }

        val clampedCount = count.coerceIn(1, 200)
        val clampedPages = pages.coerceIn(1, 10)

        val allCandles = mutableListOf<UpbitCandleDto>()
        var toParam: String? = null

        repeat(clampedPages) { pageIndex ->
            if (pageIndex > 0) delay(100)

            val page = fetchPage(candleUnit, market, clampedCount, toParam)

            if (page.isEmpty()) return@repeat

            allCandles.addAll(page)
            toParam = page.minByOrNull { it.timestamp }?.candleDateTimeUtc
        }

        val sorted = allCandles.sortedBy { it.timestamp }

        return CandleListResponse(
            market = market,
            unit = candleUnit.minutes,
            candles = sorted.map { it.toCandleResponse() },
            totalCount = sorted.size
        )
    }

    private suspend fun fetchPage(
        candleUnit: CandleUnit,
        market: String,
        count: Int,
        to: String?
    ): List<UpbitCandleDto> =
        webClient.get()
            .uri("https://api.upbit.com/v1/candles/minutes/${candleUnit.minutes}") { builder ->
                builder.queryParam("market", market)
                    .queryParam("count", count)
                    .apply { if (to != null) queryParam("to", to) }
                    .build()
            }
            .retrieve()
            .bodyToMono<List<UpbitCandleDto>>()
            .awaitSingle()

    private data class UpbitCandleDto(
        @JsonProperty("market") val market: String,
        @JsonProperty("candle_date_time_utc") val candleDateTimeUtc: String,
        @JsonProperty("candle_date_time_kst") val candleDateTimeKst: String,
        @JsonProperty("opening_price") val openingPrice: Double,
        @JsonProperty("high_price") val highPrice: Double,
        @JsonProperty("low_price") val lowPrice: Double,
        @JsonProperty("trade_price") val tradePrice: Double,
        @JsonProperty("candle_acc_trade_volume") val candleAccTradeVolume: Double,
        @JsonProperty("timestamp") val timestamp: Long
    )

    private fun UpbitCandleDto.toCandleResponse() = CandleResponse(
        market = market,
        candleDateTimeUtc = candleDateTimeUtc,
        candleDateTimeKst = candleDateTimeKst,
        openingPrice = openingPrice,
        highPrice = highPrice,
        lowPrice = lowPrice,
        tradePrice = tradePrice,
        candleAccTradeVolume = candleAccTradeVolume,
        timestamp = timestamp
    )
}
