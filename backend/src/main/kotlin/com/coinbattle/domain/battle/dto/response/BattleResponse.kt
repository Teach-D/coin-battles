package com.coinbattle.domain.battle.dto.response

import com.coinbattle.domain.battle.entity.Battle
import com.coinbattle.domain.battle.enum.BattleStatus
import java.math.BigDecimal

data class BattleResponse(
    val battleId: String,
    val status: BattleStatus,
    val hostUserId: Long,
    val leverage: Int,
    val seedMoney: Long,
    val duration: Int,
    val maxParticipants: Int,
    val currentParticipants: Int,
    val startTime: String?,
    val endTime: String?,
    val participants: List<ParticipantInfo>?,
    val winnerId: Long?,
    val createdAt: String
) {
    companion object {
        fun from(battle: Battle, participants: List<ParticipantInfo>? = null) = BattleResponse(
            battleId = battle.battleId.toString(),
            status = battle.status,
            hostUserId = battle.hostUserId,
            leverage = battle.leverage,
            seedMoney = battle.seedMoney,
            duration = battle.duration,
            maxParticipants = battle.maxParticipants,
            currentParticipants = battle.currentParticipants,
            startTime = battle.startTime?.toString(),
            endTime = battle.endTime?.toString(),
            participants = participants,
            winnerId = battle.winnerId,
            createdAt = battle.createdAt.toString()
        )
    }
}

data class ParticipantInfo(
    val userId: Long,
    val nickname: String,
    val seedPriceSnapshot: Map<String, BigDecimal>?,
    val currentValuation: Long?,
    val returnRate: Double?
)
