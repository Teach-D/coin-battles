package com.coinbattle.domain.battle.dto.response

import java.time.Instant

data class BattleWebSocketMessage(
    val type: BattleMessageType,
    val battleId: String,
    val data: BattleMessageData,
    val timestamp: String = Instant.now().toString()
)

enum class BattleMessageType {
    PARTICIPANT_JOINED,
    BATTLE_STARTED,
    RANK_UPDATE,
    BATTLE_FINISHED,
    CARD_READY
}

data class BattleMessageData(
    val userId: Long? = null,
    val currentParticipants: Int? = null,
    val rankings: List<BattleRankEntry>? = null,
    val winnerId: Long? = null
)

data class BattleRankEntry(
    val rank: Int,
    val userId: Long,
    val nickname: String,
    val returnRate: Double,
    val currentValuation: Long
)

data class MatchFoundMessage(
    val battleId: String,
    val status: String,
    val participants: List<MatchParticipant>
)

data class MatchParticipant(
    val userId: Long,
    val nickname: String
)

data class CardReadyMessage(
    val type: String = "CARD_READY",
    val battleId: String,
    val cardImageUrl: String,
    val timestamp: String = Instant.now().toString()
)
