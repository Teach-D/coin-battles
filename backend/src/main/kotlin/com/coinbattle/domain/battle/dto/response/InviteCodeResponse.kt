package com.coinbattle.domain.battle.dto.response

import java.time.Instant

data class InviteCodeResponse(
    val inviteCode: String,
    val inviteUrl: String,
    val expiresAt: Instant
)
