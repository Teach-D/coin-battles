package com.coinbattle.domain.order.controller

import com.coinbattle.common.dto.ApiResponse
import com.coinbattle.domain.order.dto.request.BuyOrderRequest
import com.coinbattle.domain.order.dto.request.SellOrderRequest
import com.coinbattle.domain.order.dto.response.OrderHistoryResponse
import com.coinbattle.domain.order.dto.response.OrderResponse
import com.coinbattle.domain.order.dto.response.PortfolioResponse
import com.coinbattle.domain.order.service.OrderService
import com.coinbattle.domain.user.entity.CoinBattlePrincipal
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/orders")
class OrderController(
    private val orderService: OrderService
) {
    @PostMapping("/buy")
    fun buy(
        @AuthenticationPrincipal principal: CoinBattlePrincipal,
        @Valid @RequestBody request: BuyOrderRequest
    ): ResponseEntity<ApiResponse<OrderResponse>> {
        val result = orderService.buy(principal.user.id, request)
        return ResponseEntity.ok(ApiResponse.ok(result))
    }

    @PostMapping("/sell")
    fun sell(
        @AuthenticationPrincipal principal: CoinBattlePrincipal,
        @Valid @RequestBody request: SellOrderRequest
    ): ResponseEntity<ApiResponse<OrderResponse>> {
        val result = orderService.sell(principal.user.id, request)
        return ResponseEntity.ok(ApiResponse.ok(result))
    }

    @GetMapping("/portfolio")
    fun getPortfolio(
        @AuthenticationPrincipal principal: CoinBattlePrincipal
    ): ResponseEntity<ApiResponse<PortfolioResponse>> {
        val result = orderService.getPortfolio(principal.user.id)
        return ResponseEntity.ok(ApiResponse.ok(result))
    }

    @GetMapping("/history")
    fun getOrderHistory(
        @AuthenticationPrincipal principal: CoinBattlePrincipal
    ): ResponseEntity<ApiResponse<List<OrderHistoryResponse>>> {
        val result = orderService.getOrderHistory(principal.user.id)
        return ResponseEntity.ok(ApiResponse.ok(result))
    }
}
