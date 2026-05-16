package com.coinbattle.domain.battle.event

import com.coinbattle.domain.battle.dto.response.BattleRankEntry
import com.coinbattle.domain.battle.dto.response.BattleResultResponse

data class BattleFinishedEvent(
    val battleId: String,
    val winnerId: Long?,
    val rankings: List<BattleRankEntry>,
    val battleResult: BattleResultResponse
)
