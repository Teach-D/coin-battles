package com.coinbattle.common.config

import com.coinbattle.domain.market.service.TickerPubSubSubscriber
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter

@Configuration
class RedisConfig(
    private val tickerPubSubSubscriber: TickerPubSubSubscriber
) {

    @Bean
    fun redisMessageListenerContainer(
        connectionFactory: RedisConnectionFactory
    ): RedisMessageListenerContainer {
        val container = RedisMessageListenerContainer()
        container.setConnectionFactory(connectionFactory)
        container.addMessageListener(messageListenerAdapter(), ChannelTopic("ticker:updates"))
        return container
    }

    @Bean
    fun messageListenerAdapter(): MessageListenerAdapter =
        MessageListenerAdapter(tickerPubSubSubscriber, "onMessage")

    @Bean
    fun objectMapper(): ObjectMapper =
        ObjectMapper()
            .registerKotlinModule()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
}
