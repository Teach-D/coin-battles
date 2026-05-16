package com.coinbattle.domain.battle.service

interface StorageClient {
    fun put(key: String, data: ByteArray, contentType: String): String
}
