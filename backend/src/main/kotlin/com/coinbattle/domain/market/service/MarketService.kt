package com.coinbattle.domain.market.service

import com.coinbattle.common.exception.CoinBattleException
import com.coinbattle.common.exception.ErrorCode
import com.coinbattle.domain.market.dto.response.TickerListResponse
import com.coinbattle.domain.market.dto.response.TickerResponse
import com.coinbattle.domain.market.repository.TickerRedisRepository
import org.springframework.stereotype.Service

@Service
class MarketService(
    private val tickerRedisRepository: TickerRedisRepository
) {
    fun getTickers(markets: List<String>?): TickerListResponse {
        val tickers = if (markets.isNullOrEmpty()) {
            tickerRedisRepository.findAll()
        } else {
            tickerRedisRepository.findByMarkets(markets)
        }
        return TickerListResponse(tickers)
    }

    fun getTicker(market: String): TickerResponse =
        tickerRedisRepository.findByMarket(market)
            ?: throw CoinBattleException(ErrorCode.TICKER_NOT_FOUND)
}
