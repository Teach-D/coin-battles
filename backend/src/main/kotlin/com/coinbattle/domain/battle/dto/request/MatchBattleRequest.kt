package com.coinbattle.domain.battle.dto.request

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull

data class MatchBattleRequest(
    @field:Min(1) @field:Max(10) val leverage: Int,
    @field:Min(10000) @field:Max(10000000) val seedMoney: Long,
    @field:NotNull val duration: Int,
    @field:NotNull val maxParticipants: Int
)
