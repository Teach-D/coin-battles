package com.coinbattle.domain.ranking

object PvpRankingFixture {

    const val DEFAULT_USER_ID = 1L
    const val DEFAULT_TICKER = "KRW-BTC"
    const val DEFAULT_LOSS_AMOUNT = 500_000L
    const val DEFAULT_LEVERAGE = 10

    fun winRateScore(wins: Long, total: Long): Double =
        if (total == 0L) 0.0 else (wins.toDouble() / total.toDouble()) * 100.0

    fun s3LiquidationPath(userId: Long, uuid: String): String =
        "liquidation-cards/$userId/$uuid.png"
}
