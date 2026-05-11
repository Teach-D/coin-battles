package com.coinbattle.domain.order.service

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class FundingRateScheduler(
    private val fundingRateService: FundingRateService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 0,8,16 * * *", zone = "UTC")
    @Async("taskExecutor")
    fun settleFundingRate() {
        log.info("funding rate settlement started")
        fundingRateService.settleAll()
        log.info("funding rate settlement completed")
    }
}
