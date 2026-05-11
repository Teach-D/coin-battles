package com.coinbattle.domain.market

import com.coinbattle.domain.market.client.BinanceWebSocketClient
import com.coinbattle.domain.market.dto.response.ChangeType
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import io.mockk.junit5.MockKExtension
import com.coinbattle.domain.market.repository.TickerRedisRepository
import com.coinbattle.domain.market.service.TickerPubSubPublisher

@ExtendWith(MockKExtension::class)
class BinanceWebSocketClientTest {

    private lateinit var client: BinanceWebSocketClient

    @BeforeEach
    fun setUp() {
        client = BinanceWebSocketClient(
            tickerRedisRepository = mockk(relaxed = true),
            tickerPubSubPublisher = mockk(relaxed = true),
            objectMapper = ObjectMapper()
        )
    }

    @Test
    fun `BTCUSDT는 USDT-BTC로 변환된다`() {
        val result = client.symbolToMarket("BTCUSDT")
        assertThat(result).isEqualTo("USDT-BTC")
    }

    @Test
    fun `ETHUSDT는 USDT-ETH로 변환된다`() {
        val result = client.symbolToMarket("ETHUSDT")
        assertThat(result).isEqualTo("USDT-ETH")
    }

    @Test
    fun `1INCHUSDT는 USDT-1INCH로 변환된다`() {
        val result = client.symbolToMarket("1INCHUSDT")
        assertThat(result).isEqualTo("USDT-1INCH")
    }

    @Test
    fun `USDT 외 quote 통화는 null을 반환한다`() {
        val result = client.symbolToMarket("ETHBTC")
        assertThat(result).isNull()
    }

    @Test
    fun `빈 문자열은 null을 반환한다`() {
        val result = client.symbolToMarket("")
        assertThat(result).isNull()
    }

    @Test
    fun `USDT만 있는 심볼은 base가 없으므로 null을 반환한다`() {
        val result = client.symbolToMarket("USDT")
        assertThat(result).isNull()
    }

    @Test
    fun `openPrice가 0이면 changeRate는 0이고 ChangeType은 EVEN이다`() {
        val raw = """[{
            "s":"BTCUSDT",
            "c":"50000.0","o":"0.0","h":"51000.0","l":"49000.0",
            "v":"100.0","q":"5000000.0","E":1700000000000
        }]"""
        val ticker = client.parseFirstTicker(raw)
        assertThat(ticker).isNotNull
        assertThat(ticker!!.changeRate).isEqualTo(0.0)
        assertThat(ticker.change).isEqualTo(ChangeType.EVEN)
    }

    @Test
    fun `closePrice가 openPrice보다 크면 ChangeType은 RISE이다`() {
        val raw = """[{
            "s":"ETHUSDT",
            "c":"2100.0","o":"2000.0","h":"2200.0","l":"1900.0",
            "v":"500.0","q":"1000000.0","E":1700000000000
        }]"""
        val ticker = client.parseFirstTicker(raw)
        assertThat(ticker).isNotNull
        assertThat(ticker!!.change).isEqualTo(ChangeType.RISE)
        assertThat(ticker.changeRate).isGreaterThan(0.0)
    }

    @Test
    fun `closePrice가 openPrice보다 작으면 ChangeType은 FALL이다`() {
        val raw = """[{
            "s":"ETHUSDT",
            "c":"1900.0","o":"2000.0","h":"2200.0","l":"1800.0",
            "v":"500.0","q":"1000000.0","E":1700000000000
        }]"""
        val ticker = client.parseFirstTicker(raw)
        assertThat(ticker).isNotNull
        assertThat(ticker!!.change).isEqualTo(ChangeType.FALL)
        assertThat(ticker.changeRate).isLessThan(0.0)
    }
}
