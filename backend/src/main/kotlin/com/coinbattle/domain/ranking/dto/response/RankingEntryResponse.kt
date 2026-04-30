package com.coinbattle.domain.ranking.dto.response

data class RankingEntryResponse(
    val rank: Int,
    val userId: Long,
    val nickname: String,
    val evaluatedValue: Long
)
