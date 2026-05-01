package com.coinbattle.domain.battle.dto.response

import com.coinbattle.domain.battle.entity.Battle
import com.coinbattle.domain.battle.enum.BattleStatus

data class BattleListResponse(
    val content: List<BattleSummary>,
    val totalElements: Long,
    val totalPages: Int,
    val page: Int,
    val size: Int
)

data class BattleSummary(
    val battleId: String,
    val status: BattleStatus,
    val leverage: Int,
    val seedMoney: Long,
    val duration: Int,
    val maxParticipants: Int,
    val currentParticipants: Int,
    val createdAt: String
) {
    companion object {
        fun from(battle: Battle) = BattleSummary(
            battleId = battle.battleId.toString(),
            status = battle.status,
            leverage = battle.leverage,
            seedMoney = battle.seedMoney,
            duration = battle.duration,
            maxParticipants = battle.maxParticipants,
            currentParticipants = battle.currentParticipants,
            createdAt = battle.createdAt.toString()
        )
    }
}
