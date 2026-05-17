package com.coinbattle.domain.battle.service

import com.coinbattle.domain.battle.dto.response.BattleResultResponse
import com.coinbattle.domain.battle.dto.response.CardReadyMessage
import com.coinbattle.domain.battle.dto.response.LiquidationCardReadyMessage
import org.slf4j.LoggerFactory
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class BattleCardPipelineService(
    private val battleCardImageService: BattleCardImageService,
    private val s3StorageService: S3StorageService,
    private val messagingTemplate: SimpMessagingTemplate
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun generateAndBroadcastCard(result: BattleResultResponse) {
        try {
            val imageBytes = battleCardImageService.generateCardImage(result)
            val key = "battle-cards/${result.battleId}/${UUID.randomUUID()}.png"
            val url = s3StorageService.upload(key, imageBytes, "image/png")
            val message = CardReadyMessage(battleId = result.battleId, cardImageUrl = url)
            result.participants.forEach { participant ->
                messagingTemplate.convertAndSendToUser(
                    participant.userId.toString(),
                    "/queue/notification",
                    message
                )
            }
        } catch (e: Exception) {
            logger.error("결과 카드 생성/브로드캐스트 실패 battleId=${result.battleId}", e)
        }
    }

    suspend fun generateAndBroadcastLiquidationCard(
        userId: Long,
        ticker: String,
        lossAmount: Long,
        leverage: Int
    ) {
        try {
            val imageBytes = battleCardImageService.generateLiquidationCard(ticker, lossAmount, leverage)
            val key = "liquidation-cards/$userId/${UUID.randomUUID()}.png"
            val url = s3StorageService.upload(key, imageBytes, "image/png")
            messagingTemplate.convertAndSendToUser(
                userId.toString(),
                "/queue/notification",
                LiquidationCardReadyMessage(ticker = ticker, cardImageUrl = url)
            )
        } catch (e: Exception) {
            logger.error("청산 카드 생성/브로드캐스트 실패 userId=$userId ticker=$ticker", e)
        }
    }
}
