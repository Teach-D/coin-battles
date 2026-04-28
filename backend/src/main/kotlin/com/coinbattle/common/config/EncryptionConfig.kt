package com.coinbattle.common.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "encryption")
class EncryptionConfig {
    lateinit var key: String
}
