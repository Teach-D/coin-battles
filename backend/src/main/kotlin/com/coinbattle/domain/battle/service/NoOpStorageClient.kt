package com.coinbattle.domain.battle.service

class NoOpStorageClient : StorageClient {
    override fun put(key: String, data: ByteArray, contentType: String): String {
        return "http://localhost:8080/mock-storage/$key"
    }
}
