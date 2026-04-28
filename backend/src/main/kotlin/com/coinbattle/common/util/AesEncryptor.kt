package com.coinbattle.common.util

import com.coinbattle.common.config.EncryptionConfig
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

@Component
class AesEncryptor(private val encryptionConfig: EncryptionConfig) {

    private val algorithm = "AES/CBC/PKCS5Padding"

    private fun getKey(): SecretKeySpec {
        val keyBytes = Base64.getDecoder().decode(encryptionConfig.key)
        val paddedKey = keyBytes.copyOf(32)
        return SecretKeySpec(paddedKey, "AES")
    }

    private fun getFixedIv(): IvParameterSpec {
        val keyBytes = Base64.getDecoder().decode(encryptionConfig.key)
        val hash = MessageDigest.getInstance("SHA-256").digest(keyBytes)
        return IvParameterSpec(hash.copyOf(16))
    }

    fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(algorithm)
        cipher.init(Cipher.ENCRYPT_MODE, getKey(), getFixedIv())
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(encrypted)
    }

    fun decrypt(cipherText: String): String {
        val cipher = Cipher.getInstance(algorithm)
        cipher.init(Cipher.DECRYPT_MODE, getKey(), getFixedIv())
        val decrypted = cipher.doFinal(Base64.getDecoder().decode(cipherText))
        return String(decrypted, Charsets.UTF_8)
    }
}
