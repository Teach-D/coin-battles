package com.coinbattle.domain.battle.dto.response

data class MatchQueueResponse(
    val queueKey: String,
    val estimatedWaitSeconds: Int
)
