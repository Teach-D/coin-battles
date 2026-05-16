package com.coinbattle.domain.user.dto.response

data class UserStatsResponse(
    val wins: Int,
    val losses: Int,
    val draws: Int,
    val totalGames: Int,
    val winRate: Double?,
    val bestReturnRate: Double?
)
