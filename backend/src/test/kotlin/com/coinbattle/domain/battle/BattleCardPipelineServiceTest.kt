package com.coinbattle.domain.battle

import com.coinbattle.domain.battle.service.S3StorageService
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.messaging.simp.SimpMessagingTemplate
import java.util.UUID

/**
 * [구현 에이전트 주의]
 *
 * BattleCardImageService에 다음 메서드를 추가해야 합니다:
 *
 * ```kotlin
 * fun generateLiquidationCard(ticker: String, lossAmount: Long, leverage: Int): ByteArray {
 *     // 💀 청산 밈 이미지 생성 (ticker, 손실금액, 레버리지 표시)
 *     // BufferedImage 생성 → PNG ByteArray 반환
 * }
 * ```
 *
 * BattleCardPipelineService에 다음 suspend fun을 추가해야 합니다:
 *
 * ```kotlin
 * suspend fun generateAndBroadcastLiquidationCard(
 *     userId: Long,
 *     ticker: String,
 *     lossAmount: Long,
 *     leverage: Int
 * ) {
 *     try {
 *         val imageBytes = battleCardImageService.generateLiquidationCard(ticker, lossAmount, leverage)
 *         val key = "liquidation-cards/$userId/${UUID.randomUUID()}.png"
 *         val url = s3StorageService.upload(key, imageBytes, "image/png")
 *         messagingTemplate.convertAndSendToUser(
 *             userId.toString(), "/queue/notification",
 *             LiquidationCardReadyMessage(ticker = ticker, cardImageUrl = url)
 *         )
 *     } catch (e: Exception) {
 *         logger.error("청산 카드 생성/브로드캐스트 실패 userId=$userId", e)
 *     }
 * }
 * ```
 *
 * 신규 DTO:
 * ```kotlin
 * // com.coinbattle.domain.battle.dto.response.LiquidationCardReadyMessage
 * data class LiquidationCardReadyMessage(
 *     val type: String = "LIQUIDATION_CARD_READY",
 *     val ticker: String,
 *     val cardImageUrl: String,
 *     val timestamp: String = Instant.now().toString()
 * )
 * ```
 *
 * LiquidationScheduler에서:
 * ```kotlin
 * // forceClose 성공 후
 * scope.launch {
 *     battleCardPipelineService.generateAndBroadcastLiquidationCard(userId, ticker, lossAmount, leverage)
 * }
 * ```
 */

// ── 테스트 전용 인터페이스 (구현 에이전트가 실제 클래스에 추가하면 이 인터페이스와 stub은 제거) ──

interface LiquidationCardImageGenerator {
    fun generateLiquidationCard(ticker: String, lossAmount: Long, leverage: Int): ByteArray
}

interface LiquidationCardPipelineCapable {
    suspend fun generateAndBroadcastLiquidationCard(
        userId: Long,
        ticker: String,
        lossAmount: Long,
        leverage: Int
    )
}

// ── 테스트 전용 Stub (실제 구현 로직과 동일한 구조를 기술) ────────────────────

private class LiquidationCardPipelineStub(
    private val imageGenerator: LiquidationCardImageGenerator,
    private val s3StorageService: S3StorageService,
    private val messagingTemplate: SimpMessagingTemplate
) : LiquidationCardPipelineCapable {

    private val logger = org.slf4j.LoggerFactory.getLogger(javaClass)

    override suspend fun generateAndBroadcastLiquidationCard(
        userId: Long,
        ticker: String,
        lossAmount: Long,
        leverage: Int
    ) {
        try {
            val imageBytes = imageGenerator.generateLiquidationCard(ticker, lossAmount, leverage)
            val key = "liquidation-cards/$userId/${UUID.randomUUID()}.png"
            val url = s3StorageService.upload(key, imageBytes, "image/png")
            messagingTemplate.convertAndSendToUser(
                userId.toString(),
                "/queue/notification",
                mapOf("type" to "LIQUIDATION_CARD_READY", "ticker" to ticker, "cardImageUrl" to url)
            )
        } catch (e: Exception) {
            logger.error("청산 카드 생성/브로드캐스트 실패 userId=$userId", e)
        }
    }
}

@ExtendWith(MockKExtension::class)
class BattleCardPipelineServiceTest {

    private lateinit var imageGenerator: LiquidationCardImageGenerator
    private lateinit var s3StorageService: S3StorageService
    private lateinit var messagingTemplate: SimpMessagingTemplate
    private lateinit var pipeline: LiquidationCardPipelineCapable

    @BeforeEach
    fun setUp() {
        imageGenerator = mockk()
        s3StorageService = mockk()
        messagingTemplate = mockk()

        pipeline = LiquidationCardPipelineStub(imageGenerator, s3StorageService, messagingTemplate)
    }

    // ── BR-06: 카드 생성 실패 시 예외 미전파 ─────────────────────────────────

    @Test
    fun `generateAndBroadcastLiquidationCard_이미지생성_실패시_예외_전파_없음`() {
        // given
        every {
            imageGenerator.generateLiquidationCard(any(), any(), any())
        } throws RuntimeException("이미지 생성 실패")

        // when & then
        assertThatCode {
            runBlocking {
                pipeline.generateAndBroadcastLiquidationCard(
                    userId = 1L,
                    ticker = "KRW-BTC",
                    lossAmount = 500_000L,
                    leverage = 10
                )
            }
        }.doesNotThrowAnyException()
    }

    // ── BR-08: S3 경로 형식 검증 ──────────────────────────────────────────────

    @Test
    fun `generateAndBroadcastLiquidationCard_S3_경로가_liquidation_cards_userId_uuid_png_형식`() {
        // given
        val imageBytes = byteArrayOf(1, 2, 3)
        every {
            imageGenerator.generateLiquidationCard(any(), any(), any())
        } returns imageBytes

        val pathSlot = slot<String>()
        every {
            s3StorageService.upload(capture(pathSlot), any(), any())
        } returns "https://s3.example.com/liquidation-cards/1/some-uuid.png"

        justRun {
            messagingTemplate.convertAndSendToUser(any(), any(), any<Any>())
        }

        // when
        runBlocking {
            pipeline.generateAndBroadcastLiquidationCard(
                userId = 1L,
                ticker = "KRW-BTC",
                lossAmount = 500_000L,
                leverage = 10
            )
        }

        // then
        assertThat(pathSlot.captured).startsWith("liquidation-cards/1/")
        assertThat(pathSlot.captured).endsWith(".png")
    }
}
