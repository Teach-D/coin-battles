package com.coinbattle

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class CoinbattleApplication

fun main(args: Array<String>) {
    runApplication<CoinbattleApplication>(*args)
}
