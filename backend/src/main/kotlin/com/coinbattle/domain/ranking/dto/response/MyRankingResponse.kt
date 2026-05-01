package com.coinbattle.domain.ranking.dto.response

data class MyRankingResponse(
    val userId: Long,
    val nickname: String,
    val season: RankingSlot,
    val daily: RankingSlot
) {
    data class RankingSlot(
        val rank: Long?,
        val evaluatedValue: Long
    )
}
