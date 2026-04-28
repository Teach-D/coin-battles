package com.coinbattle.domain.market.service

import com.coinbattle.domain.market.dto.response.TickerResponse
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service

@Service
class TickerPubSubPublisher(
    private val stringRedisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper
) {
    fun publish(ticker: TickerResponse) {
        val json = objectMapper.writeValueAsString(ticker)
        stringRedisTemplate.convertAndSend("ticker:updates", json)
    }
}
