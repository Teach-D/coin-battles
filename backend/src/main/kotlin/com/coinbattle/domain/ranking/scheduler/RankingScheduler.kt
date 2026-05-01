package com.coinbattle.domain.ranking.scheduler

import com.coinbattle.domain.ranking.service.RankingService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class RankingScheduler(
    private val rankingService: RankingService
) {
    @Scheduled(cron = "0 0 0 * * *", zone = "UTC")
    fun resetDailyRanking() {
        rankingService.resetDailyRanking()
    }
}
