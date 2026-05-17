package com.coinbattle.domain.ranking.controller

import com.coinbattle.common.dto.ApiResponse
import com.coinbattle.common.exception.CoinBattleException
import com.coinbattle.common.exception.ErrorCode
import com.coinbattle.domain.ranking.dto.response.MyRankingResponse
import com.coinbattle.domain.ranking.dto.response.PvpRankingEntryResponse
import com.coinbattle.domain.ranking.dto.response.RankingEntryResponse
import com.coinbattle.domain.ranking.service.RankingService
import com.coinbattle.domain.user.entity.CoinBattlePrincipal
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

enum class RankingType { SEASON, DAILY, PVP }

@RestController
class RankingController(
    private val rankingService: RankingService
) {
    @GetMapping("/api/ranking/season")
    fun getSeasonRanking(
        @RequestParam(defaultValue = "100") limit: Int
    ): ApiResponse<List<RankingEntryResponse>> {
        return ApiResponse.ok(rankingService.getTopRankings(RankingService.SEASON_KEY, limit))
    }

    @GetMapping("/api/ranking/daily")
    fun getDailyRanking(
        @RequestParam(defaultValue = "100") limit: Int
    ): ApiResponse<List<RankingEntryResponse>> {
        return ApiResponse.ok(rankingService.getTopRankings(RankingService.DAILY_KEY, limit))
    }

    @GetMapping("/api/ranking/me")
    fun getMyRanking(
        @AuthenticationPrincipal principal: CoinBattlePrincipal
    ): ApiResponse<MyRankingResponse> {
        return ApiResponse.ok(rankingService.getMyRanking(principal.user.id))
    }

    @GetMapping("/api/rankings")
    fun getRankings(
        @RequestParam(defaultValue = "SEASON") type: RankingType,
        @RequestParam(defaultValue = "100") limit: Int
    ): ApiResponse<*> {
        return when (type) {
            RankingType.SEASON -> ApiResponse.ok(rankingService.getTopRankings(RankingService.SEASON_KEY, limit))
            RankingType.DAILY -> ApiResponse.ok(rankingService.getTopRankings(RankingService.DAILY_KEY, limit))
            RankingType.PVP -> ApiResponse.ok(rankingService.getTopPvpRankings(limit))
        }
    }
}
