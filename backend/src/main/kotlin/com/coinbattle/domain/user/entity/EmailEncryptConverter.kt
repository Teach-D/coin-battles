package com.coinbattle.domain.user.entity

import com.coinbattle.common.util.AesEncryptor
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import org.springframework.stereotype.Component

@Converter
@Component
class EmailEncryptConverter(private val aesEncryptor: AesEncryptor) : AttributeConverter<String, String> {
    override fun convertToDatabaseColumn(attribute: String?): String? =
        attribute?.let { aesEncryptor.encrypt(it) }

    override fun convertToEntityAttribute(dbData: String?): String? =
        dbData?.let { aesEncryptor.decrypt(it) }
}
