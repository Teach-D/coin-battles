package com.coinbattle.domain.battle.dto.response

import java.time.Instant

data class JoinByInviteResponse(
    val battleId: String,
    val battleRoomUrl: String,
    val joinedAt: Instant
)
