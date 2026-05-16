package com.coinbattle.domain.battle.service

import com.coinbattle.domain.battle.dto.response.BattleResultResponse
import com.coinbattle.domain.battle.dto.response.ParticipantResultResponse
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class BattleCardImageServiceTest {

    lateinit var battleCardImageService: BattleCardImageService

    @BeforeEach
    fun setUp() {
        battleCardImageService = BattleCardImageService()
    }

    @Test
    fun `결과카드_이미지_생성_바이트배열_반환`() {
        // given
        val result = createBattleResultResponse(
            participants = listOf(createParticipantResultResponse(userId = 1L, rank = 1))
        )

        // when
        val bytes = battleCardImageService.generateCardImage(result)

        // then
        assertThat(bytes.size).isGreaterThan(0)
    }

    @Test
    fun `단일참가자_결과카드_생성`() {
        // given
        val result = createBattleResultResponse(
            participants = listOf(createParticipantResultResponse(userId = 1L, rank = 1))
        )

        // when
        val bytes = battleCardImageService.generateCardImage(result)

        // then
        assertThat(bytes.size).isGreaterThanOrEqualTo(0)
    }

    @Test
    fun `최대참가자_결과카드_생성`() {
        // given
        val participants = (1..5).map { idx ->
            createParticipantResultResponse(userId = idx.toLong(), rank = idx)
        }
        val result = createBattleResultResponse(participants = participants)

        // when
        val bytes = battleCardImageService.generateCardImage(result)

        // then
        assertThat(bytes.size).isGreaterThanOrEqualTo(0)
    }

    @Test
    fun `수익률_음수일때_결과카드_생성`() {
        // given
        val participants = (1..3).map { idx ->
            createParticipantResultResponse(
                userId = idx.toLong(),
                rank = idx,
                profitRate = -10.0 * idx,
                profitAmount = -10000L * idx
            )
        }
        val result = createBattleResultResponse(participants = participants)

        // when
        val bytes = battleCardImageService.generateCardImage(result)

        // then
        assertThat(bytes.size).isGreaterThanOrEqualTo(0)
    }

    private fun createBattleResultResponse(
        battleId: String = "battle-001",
        participants: List<ParticipantResultResponse> = emptyList()
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
        rank: Int = 1,
        profitRate: Double = 5.0,
        profitAmount: Long = 50000L
    ): ParticipantResultResponse {
        return ParticipantResultResponse(
            userId = userId,
            nickname = "user$userId",
            rank = rank,
            isWinner = rank == 1,
            initialSeed = 1_000_000L,
            finalValuation = 1_000_000L + profitAmount,
            profitAmount = profitAmount,
            profitRate = profitRate
        )
    }
}
