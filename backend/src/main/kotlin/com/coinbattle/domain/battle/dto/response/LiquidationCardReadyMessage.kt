package com.coinbattle.domain.battle.dto.response

import java.time.Instant

data class LiquidationCardReadyMessage(
    val type: String = "LIQUIDATION_CARD_READY",
    val ticker: String,
    val cardImageUrl: String,
    val timestamp: String = Instant.now().toString()
)
