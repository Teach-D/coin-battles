package com.coinbattle.domain.market.repository

import com.coinbattle.domain.market.dto.response.ChangeType
import com.coinbattle.domain.market.dto.response.TickerResponse
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import java.time.Duration
import java.time.Instant

@Repository
class TickerRedisRepository(
    private val stringRedisTemplate: StringRedisTemplate
) {
    private val ttl = Duration.ofSeconds(3)
    private val MARKETS_KEY = "ticker:markets"

    fun save(ticker: TickerResponse) {
        val key = "ticker:${ticker.market}"
        val ops = stringRedisTemplate.opsForHash<String, String>()
        val fields = mapOf(
            "market" to ticker.market,
            "tradePrice" to ticker.tradePrice.toString(),
            "changeRate" to ticker.changeRate.toString(),
            "changePrice" to ticker.changePrice.toString(),
            "change" to ticker.change.name,
            "accTradeVolume24h" to ticker.accTradeVolume24h.toString(),
            "accTradePrice24h" to ticker.accTradePrice24h.toString(),
            "highPrice" to ticker.highPrice.toString(),
            "lowPrice" to ticker.lowPrice.toString(),
            "cachedAt" to Instant.now().toString(),
            "timestamp" to (ticker.timestamp?.toString() ?: "")
        )
        ops.putAll(key, fields)
        stringRedisTemplate.expire(key, ttl)
    }

    fun findByMarket(market: String): TickerResponse? {
        val key = "ticker:$market"
        val fields = stringRedisTemplate.opsForHash<String, String>().entries(key)
        if (fields.isEmpty()) return null
        return fields.toTickerResponse()
    }

    fun saveMarkets(markets: List<String>) {
        stringRedisTemplate.opsForSet().add(MARKETS_KEY, *markets.toTypedArray())
    }

    fun findAll(): List<TickerResponse> {
        val markets = stringRedisTemplate.opsForSet().members(MARKETS_KEY) ?: return emptyList()
        return markets.mapNotNull { findByMarket(it) }
    }

    fun findByMarkets(markets: List<String>): List<TickerResponse> =
        markets.mapNotNull { findByMarket(it) }

    private fun Map<String, String>.toTickerResponse(): TickerResponse? {
        val market = this["market"] ?: return null
        return TickerResponse(
            market = market,
            tradePrice = this["tradePrice"]?.toDoubleOrNull() ?: return null,
            changeRate = this["changeRate"]?.toDoubleOrNull() ?: return null,
            changePrice = this["changePrice"]?.toDoubleOrNull() ?: return null,
            change = this["change"]?.let { runCatching { ChangeType.valueOf(it) }.getOrNull() } ?: return null,
            accTradeVolume24h = this["accTradeVolume24h"]?.toDoubleOrNull() ?: return null,
            accTradePrice24h = this["accTradePrice24h"]?.toDoubleOrNull() ?: return null,
            highPrice = this["highPrice"]?.toDoubleOrNull() ?: return null,
            lowPrice = this["lowPrice"]?.toDoubleOrNull() ?: return null,
            cachedAt = this["cachedAt"]?.let { runCatching { Instant.parse(it) }.getOrNull() },
            timestamp = this["timestamp"]?.toLongOrNull()
        )
    }
}
