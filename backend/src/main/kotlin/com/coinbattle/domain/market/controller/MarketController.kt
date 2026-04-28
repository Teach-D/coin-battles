package com.coinbattle.domain.market.controller

import com.coinbattle.common.dto.ApiResponse
import com.coinbattle.domain.market.dto.response.TickerListResponse
import com.coinbattle.domain.market.dto.response.TickerResponse
import com.coinbattle.domain.market.service.MarketService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/market")
class MarketController(
    private val marketService: MarketService
) {

    @GetMapping("/tickers")
    fun getTickers(
        @RequestParam(required = false) markets: String?
    ): ResponseEntity<ApiResponse<TickerListResponse>> {
        val marketList = markets?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
        return ResponseEntity.ok(ApiResponse.ok(marketService.getTickers(marketList)))
    }

    @GetMapping("/tickers/{market}")
    fun getTicker(
        @PathVariable market: String
    ): ResponseEntity<ApiResponse<TickerResponse>> =
        ResponseEntity.ok(ApiResponse.ok(marketService.getTicker(market)))
}
