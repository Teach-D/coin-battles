package com.coinbattle.domain.battle.dto.response

import com.coinbattle.domain.battle.entity.Battle
import com.coinbattle.domain.battle.enum.BattleStatus

data class JoinBattleResponse(
    val battleId: String,
    val status: BattleStatus,
    val currentParticipants: Int,
    val maxParticipants: Int,
    val startTime: String?
) {
    companion object {
        fun from(battle: Battle) = JoinBattleResponse(
            battleId = battle.battleId.toString(),
            status = battle.status,
            currentParticipants = battle.currentParticipants,
            maxParticipants = battle.maxParticipants,
            startTime = battle.startTime?.toString()
        )
    }
}
