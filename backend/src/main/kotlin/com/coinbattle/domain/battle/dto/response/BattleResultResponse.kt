package com.coinbattle.domain.battle.dto.response

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class BattleResultResponse(
    val battleId: String,
    val status: String,
    val durationMinutes: Int,
    val endedAt: String?,
    val participants: List<ParticipantResultResponse>,
    val myResult: ParticipantResultResponse?
)

data class ParticipantResultResponse(
    val userId: Long,
    val nickname: String,
    val rank: Int,
    val isWinner: Boolean,
    val initialSeed: Long,
    val finalValuation: Long,
    val profitAmount: Long,
    val profitRate: Double
)
