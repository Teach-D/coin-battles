package com.coinbattle

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties
class CoinbattleApplication

fun main(args: Array<String>) {
    runApplication<CoinbattleApplication>(*args)
}
