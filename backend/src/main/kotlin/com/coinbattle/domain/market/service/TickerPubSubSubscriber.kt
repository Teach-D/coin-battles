package com.coinbattle.domain.market.service

import com.coinbattle.domain.market.dto.response.TickerResponse
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PostConstruct
import org.springframework.data.redis.connection.Message
import org.springframework.data.redis.connection.MessageListener
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service

@Service
class TickerPubSubSubscriber(
    private val simpMessagingTemplate: SimpMessagingTemplate,
    private val objectMapper: ObjectMapper,
    private val redisMessageListenerContainer: RedisMessageListenerContainer
) : MessageListener {

    @PostConstruct
    fun registerListener() {
        redisMessageListenerContainer.addMessageListener(this, ChannelTopic("ticker:updates"))
    }

    fun onMessage(message: String) {
        val ticker = objectMapper.readValue(message, TickerResponse::class.java)
        simpMessagingTemplate.convertAndSend("/topic/ticker/${ticker.market}", ticker)
    }

    override fun onMessage(message: Message, pattern: ByteArray?) {
        onMessage(String(message.body))
    }
}
