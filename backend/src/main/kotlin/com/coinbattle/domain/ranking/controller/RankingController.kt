package com.coinbattle.domain.ranking.controller

import com.coinbattle.common.dto.ApiResponse
import com.coinbattle.domain.ranking.dto.response.MyRankingResponse
import com.coinbattle.domain.ranking.dto.response.RankingEntryResponse
import com.coinbattle.domain.ranking.service.RankingService
import com.coinbattle.domain.user.entity.User
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/ranking")
class RankingController(
    private val rankingService: RankingService
) {
    @GetMapping("/season")
    fun getSeasonRanking(
        @RequestParam(defaultValue = "100") limit: Int
    ): ApiResponse<List<RankingEntryResponse>> {
        return ApiResponse.ok(rankingService.getTopRankings(RankingService.SEASON_KEY, limit))
    }

    @GetMapping("/daily")
    fun getDailyRanking(
        @RequestParam(defaultValue = "100") limit: Int
    ): ApiResponse<List<RankingEntryResponse>> {
        return ApiResponse.ok(rankingService.getTopRankings(RankingService.DAILY_KEY, limit))
    }

    @GetMapping("/me")
    fun getMyRanking(
        @AuthenticationPrincipal user: User
    ): ApiResponse<MyRankingResponse> {
        return ApiResponse.ok(rankingService.getMyRanking(user.id))
    }
}
