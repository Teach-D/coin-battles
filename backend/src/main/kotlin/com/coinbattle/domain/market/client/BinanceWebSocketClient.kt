package com.coinbattle.domain.market.client

import com.coinbattle.domain.market.dto.response.ChangeType
import com.coinbattle.domain.market.dto.response.TickerResponse
import com.coinbattle.domain.market.repository.TickerRedisRepository
import com.coinbattle.domain.market.service.TickerPubSubPublisher
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import org.springframework.web.reactive.socket.WebSocketMessage
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient
import java.net.URI

@Component
class BinanceWebSocketClient(
    private val tickerRedisRepository: TickerRedisRepository,
    private val tickerPubSubPublisher: TickerPubSubPublisher,
    private val objectMapper: ObjectMapper
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)
    private val wsClient = ReactorNettyWebSocketClient()
    private val scope = CoroutineScope(Dispatchers.IO)

    @Volatile
    private var marketsRegistered = false

    override fun run(args: ApplicationArguments) {
        scope.launch { connectWithBackoff() }
    }

    private suspend fun connectWithBackoff() {
        val delays = longArrayOf(1_000, 2_000, 4_000, 8_000, 16_000, 30_000)
        var attempt = 0
        while (true) {
            runCatching { connect() }
                .onFailure { log.warn("바이낸스 WebSocket 연결 실패 (attempt={}): {}", attempt + 1, it.message) }
            val waitMs = delays[minOf(attempt, delays.size - 1)]
            attempt++
            delay(waitMs)
        }
    }

    private suspend fun connect() {
        marketsRegistered = false
        log.info("바이낸스 WebSocket 연결 시작")
        wsClient.execute(URI.create("wss://stream.binance.com:9443/ws/!miniTicker@arr")) { session ->
            session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .doOnNext { handleMessage(it) }
                .doOnError { log.warn("바이낸스 메시지 수신 오류: {}", it.message) }
                .then()
        }.block()
    }

    private fun handleMessage(raw: String) {
        runCatching {
            val root = objectMapper.readTree(raw)
            if (!root.isArray) return

            val markets = mutableListOf<String>()
            root.forEach { node ->
                val ticker = node.toTickerResponse() ?: return@forEach
                markets.add(ticker.market)
                tickerRedisRepository.save(ticker)
                tickerPubSubPublisher.publish(ticker)
            }

            if (!marketsRegistered && markets.isNotEmpty()) {
                tickerRedisRepository.saveMarkets(markets)
                marketsRegistered = true
            }
        }.onFailure { log.debug("바이낸스 메시지 파싱 실패: {}", it.message) }
    }

    internal fun parseFirstTicker(raw: String): TickerResponse? {
        val root = objectMapper.readTree(raw)
        if (!root.isArray) return null
        return root.firstOrNull()?.toTickerResponse()
    }

    internal fun symbolToMarket(symbol: String): String? {
        val upper = symbol.uppercase()
        val quote = listOf("USDT").firstOrNull { upper.endsWith(it) } ?: return null
        val base = upper.removeSuffix(quote)
        return if (base.isEmpty()) null else "$quote-$base"
    }

    private fun JsonNode.toTickerResponse(): TickerResponse? {
        val symbol = this["s"]?.asText() ?: return null
        val market = symbolToMarket(symbol) ?: return null

        val closePrice = this["c"]?.asDouble() ?: return null
        val openPrice = this["o"]?.asDouble() ?: return null
        val highPrice = this["h"]?.asDouble() ?: return null
        val lowPrice = this["l"]?.asDouble() ?: return null
        val baseVolume = this["v"]?.asDouble() ?: return null
        val quoteVolume = this["q"]?.asDouble() ?: return null
        val timestamp = this["E"]?.asLong()

        val changePrice = closePrice - openPrice
        val changeRate = if (openPrice != 0.0) changePrice / openPrice else 0.0
        val change = when {
            changeRate > 0 -> ChangeType.RISE
            changeRate < 0 -> ChangeType.FALL
            else -> ChangeType.EVEN
        }

        return TickerResponse(
            market = market,
            tradePrice = closePrice,
            changeRate = changeRate,
            changePrice = changePrice,
            change = change,
            accTradeVolume24h = baseVolume,
            accTradePrice24h = quoteVolume,
            highPrice = highPrice,
            lowPrice = lowPrice,
            timestamp = timestamp
        )
    }
}
