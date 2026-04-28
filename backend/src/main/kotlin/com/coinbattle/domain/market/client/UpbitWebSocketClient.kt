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
import reactor.core.publisher.Mono
import java.net.URI

@Component
class UpbitWebSocketClient(
    private val tickerRedisRepository: TickerRedisRepository,
    private val tickerPubSubPublisher: TickerPubSubPublisher,
    private val objectMapper: ObjectMapper
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)
    private val wsClient = ReactorNettyWebSocketClient()
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun run(args: ApplicationArguments) {
        scope.launch { connectWithBackoff() }
    }

    private suspend fun connectWithBackoff() {
        val delays = longArrayOf(1_000, 2_000, 4_000, 8_000, 16_000, 30_000)
        var attempt = 0
        while (true) {
            runCatching { connect() }
                .onFailure { log.warn("업비트 WebSocket 연결 실패 (attempt={}): {}", attempt + 1, it.message) }
            val waitMs = delays[minOf(attempt, delays.size - 1)]
            attempt++
            delay(waitMs)
        }
    }

    private suspend fun connect() {
        val markets = fetchKrwMarkets()
        if (markets.isEmpty()) {
            log.warn("KRW 마켓 목록 조회 실패 — 연결 중단")
            return
        }

        val subscribePayload = buildSubscribePayload(markets)
        log.info("업비트 WebSocket 연결 시작 — 마켓 {}개", markets.size)

        wsClient.execute(URI.create("wss://api.upbit.com/websocket/v1")) { session ->
            session.send(
                Mono.just(session.textMessage(subscribePayload))
            ).thenMany(
                session.receive()
                    .map(WebSocketMessage::getPayloadAsText)
                    .doOnNext { handleMessage(it) }
                    .doOnError { log.warn("업비트 메시지 수신 오류: {}", it.message) }
            ).then()
        }.block()
    }

    private fun fetchKrwMarkets(): List<String> {
        return runCatching {
            val url = "https://api.upbit.com/v1/market/all?isDetails=false"
            val response = java.net.URL(url).readText()
            val root = objectMapper.readTree(response)
            root.mapNotNull { node ->
                node["market"]?.asText()?.takeIf { it.startsWith("KRW-") }
            }
        }.getOrElse {
            log.error("업비트 마켓 목록 조회 실패: {}", it.message)
            emptyList()
        }
    }

    private fun buildSubscribePayload(markets: List<String>): String {
        val codesJson = markets.joinToString(",") { "\"$it\"" }
        return """[{"ticket":"coinbattle-server"},{"type":"ticker","codes":[$codesJson]}]"""
    }

    private fun handleMessage(raw: String) {
        runCatching {
            val node = objectMapper.readTree(raw)
            val ticker = node.toTickerResponse() ?: return
            tickerRedisRepository.save(ticker)
            tickerPubSubPublisher.publish(ticker)
        }.onFailure { log.debug("업비트 메시지 파싱 실패: {}", it.message) }
    }

    private fun JsonNode.toTickerResponse(): TickerResponse? {
        val market = this["code"]?.asText() ?: return null
        val changeRaw = this["change"]?.asText() ?: "EVEN"
        val change = runCatching { ChangeType.valueOf(changeRaw) }.getOrDefault(ChangeType.EVEN)
        return TickerResponse(
            market = market,
            tradePrice = this["trade_price"]?.asDouble() ?: return null,
            changeRate = this["signed_change_rate"]?.asDouble() ?: return null,
            changePrice = this["signed_change_price"]?.asDouble() ?: return null,
            change = change,
            accTradeVolume24h = this["acc_trade_volume_24h"]?.asDouble() ?: return null,
            accTradePrice24h = this["acc_trade_price_24h"]?.asDouble() ?: return null,
            highPrice = this["high_price"]?.asDouble() ?: return null,
            lowPrice = this["low_price"]?.asDouble() ?: return null,
            timestamp = this["timestamp"]?.asLong()
        )
    }
}
