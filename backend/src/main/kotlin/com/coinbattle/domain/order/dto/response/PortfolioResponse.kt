package com.coinbattle.domain.order.dto.response

data class PortfolioResponse(
    val portfolio: PortfolioSummary,
    val positions: List<PositionResponse>
) {
    companion object {
        fun of(userId: Long, balance: Long, positions: List<PositionResponse>): PortfolioResponse {
            val totalPnl = positions.sumOf { it.unrealizedPnl }
            val totalAsset = balance + positions.sumOf { it.evaluatedValue }
            val initialAsset = totalAsset - totalPnl
            val totalPnlRate = if (initialAsset > 0) totalPnl.toDouble() / initialAsset * 100.0 else 0.0

            return PortfolioResponse(
                portfolio = PortfolioSummary(
                    userId = userId,
                    balance = balance,
                    totalAsset = totalAsset,
                    totalPnl = totalPnl,
                    totalPnlRate = totalPnlRate
                ),
                positions = positions
            )
        }
    }
}

data class PortfolioSummary(
    val userId: Long,
    val balance: Long,
    val totalAsset: Long,
    val totalPnl: Long,
    val totalPnlRate: Double
)
