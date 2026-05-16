package com.coinbattle.domain.battle.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class S3StorageServiceTest {

    private val storageClient: StorageClient = mockk()
    lateinit var s3StorageService: S3StorageService

    @BeforeEach
    fun setUp() {
        s3StorageService = S3StorageService(storageClient)
    }

    @Test
    fun `업로드_성공시_URL_반환`() {
        // given
        val key = "battle-cards/battle-001/card.png"
        val data = ByteArray(128) { it.toByte() }
        val contentType = "image/png"
        val expectedUrl = "https://cdn.coinbattle.io/$key"
        every { storageClient.put(key, data, contentType) } returns expectedUrl

        // when
        val url = s3StorageService.upload(key, data, contentType)

        // then
        assertThat(url.length).isGreaterThan(0)
    }

    @Test
    fun `업로드_실패시_예외_전파`() {
        // given
        val key = "battle-cards/battle-002/card.png"
        val data = ByteArray(64) { it.toByte() }
        val contentType = "image/png"
        every { storageClient.put(key, data, contentType) } throws RuntimeException("S3 연결 실패")

        // when & then
        assertThatThrownBy { s3StorageService.upload(key, data, contentType) }
            .isInstanceOf(RuntimeException::class.java)
    }
}
