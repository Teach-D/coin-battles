package com.coinbattle.domain.ranking.dto.response

data class PvpRankingEntryResponse(
    val rank: Int,
    val userId: Long,
    val nickname: String,
    val winRatePct: Double
)
