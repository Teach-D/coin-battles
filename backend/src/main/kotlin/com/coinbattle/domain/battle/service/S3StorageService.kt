package com.coinbattle.domain.battle.service

import org.springframework.stereotype.Service

@Service
class S3StorageService(
    private val storageClient: StorageClient
) {
    fun upload(key: String, data: ByteArray, contentType: String): String {
        return storageClient.put(key, data, contentType)
    }
}
