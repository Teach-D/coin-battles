package com.coinbattle.domain.battle.service

import com.coinbattle.domain.battle.dto.response.BattleResultResponse
import com.coinbattle.domain.battle.dto.response.ParticipantResultResponse
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.messaging.simp.SimpMessagingTemplate

@ExtendWith(MockKExtension::class)
class BattleEndServiceCardTest {

    private val battleCardImageService: BattleCardImageService = mockk()
    private val s3StorageService: S3StorageService = mockk()
    private val messagingTemplate: SimpMessagingTemplate = mockk()
    lateinit var battleCardPipelineService: BattleCardPipelineService

    @BeforeEach
    fun setUp() {
        battleCardPipelineService = BattleCardPipelineService(
            battleCardImageService,
            s3StorageService,
            messagingTemplate
        )
    }

    @Test
    fun `배틀종료시_카드생성_비동기_트리거`() {
        // given
        val battleResult = createBattleResultResponse()
        val imageBytes = ByteArray(256) { it.toByte() }
        every { battleCardImageService.generateCardImage(battleResult) } returns imageBytes
        every { s3StorageService.upload(any(), any(), any()) } returns "https://cdn.coinbattle.io/card.png"
        justRun { messagingTemplate.convertAndSendToUser(any(), any(), any<Any>()) }

        // when
        battleCardPipelineService.generateAndBroadcastCard(battleResult)

        // then
        verify(exactly = 1) { battleCardImageService.generateCardImage(battleResult) }
    }

    @Test
    fun `카드생성완료시_CARD_READY_STOMP_발송`() {
        // given
        val participants = listOf(
            createParticipantResultResponse(userId = 1L),
            createParticipantResultResponse(userId = 2L, rank = 2)
        )
        val battleResult = createBattleResultResponse(participants = participants)
        val imageBytes = ByteArray(256) { it.toByte() }
        val cardUrl = "https://cdn.coinbattle.io/battle-001/card.png"
        every { battleCardImageService.generateCardImage(battleResult) } returns imageBytes
        every { s3StorageService.upload(any(), any(), any()) } returns cardUrl
        justRun { messagingTemplate.convertAndSendToUser(any(), any(), any<Any>()) }

        // when
        battleCardPipelineService.generateAndBroadcastCard(battleResult)

        // then
        verify(exactly = participants.size) {
            messagingTemplate.convertAndSendToUser(
                any(),
                "/queue/notification",
                any<Any>()
            )
        }
    }

    private fun createBattleResultResponse(
        battleId: String = "battle-001",
        participants: List<ParticipantResultResponse> = listOf(
            createParticipantResultResponse(userId = 1L)
        )
    ): BattleResultResponse {
        return BattleResultResponse(
            battleId = battleId,
            status = "FINISHED",
            durationMinutes = 10,
            endedAt = "2026-05-16T10:00:00",
            participants = participants,
            myResult = participants.firstOrNull()
        )
    }

    private fun createParticipantResultResponse(
        userId: Long = 1L,
        rank: Int = 1
    ): ParticipantResultResponse {
        return ParticipantResultResponse(
            userId = userId,
            nickname = "user$userId",
            rank = rank,
            isWinner = rank == 1,
            initialSeed = 1_000_000L,
            finalValuation = 1_050_000L,
            profitAmount = 50_000L,
            profitRate = 5.0
        )
    }
}
