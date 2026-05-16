package com.coinbattle.common.config

import com.coinbattle.domain.battle.service.NoOpStorageClient
import com.coinbattle.domain.battle.service.StorageClient
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class StorageConfig {
    @Bean
    @ConditionalOnMissingBean(StorageClient::class)
    fun storageClient(): StorageClient = NoOpStorageClient()
}
