package com.coinbattle.domain.battle

import com.coinbattle.domain.battle.dto.response.BattleWebSocketMessage
import com.coinbattle.domain.battle.entity.Battle
import com.coinbattle.domain.battle.enum.BattleStatus
import com.coinbattle.domain.battle.repository.BattleRepository
import com.coinbattle.domain.battle.repository.BattleSessionRepository
import com.coinbattle.domain.battle.scheduler.BattleRankingScheduler
import com.coinbattle.domain.market.dto.response.ChangeType
import com.coinbattle.domain.market.dto.response.TickerResponse
import com.coinbattle.domain.market.repository.TickerRedisRepository
import com.coinbattle.domain.order.entity.OrderDirection
import com.coinbattle.domain.order.entity.Position
import com.coinbattle.domain.order.entity.PositionStatus
import com.coinbattle.domain.order.repository.PositionRepository
import com.coinbattle.domain.user.entity.AuthProvider
import com.coinbattle.domain.user.entity.User
import com.coinbattle.domain.user.repository.UserRepository
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.messaging.simp.SimpMessagingTemplate
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@ExtendWith(MockKExtension::class)
class BattleRankingSchedulerTest {

    @MockK
    lateinit var battleRepository: BattleRepository

    @MockK
    lateinit var battleSessionRepository: BattleSessionRepository

    @MockK
    lateinit var userRepository: UserRepository

    @MockK
    lateinit var positionRepository: PositionRepository

    @MockK
    lateinit var tickerRedisRepository: TickerRedisRepository

    @MockK
    lateinit var messagingTemplate: SimpMessagingTemplate

    lateinit var scheduler: BattleRankingScheduler

    @BeforeEach
    fun setUp() {
        scheduler = BattleRankingScheduler(
            battleRepository,
            battleSessionRepository,
            userRepository,
            positionRepository,
            tickerRedisRepository,
            messagingTemplate
        )
    }

    @Test
    fun `IN_PROGRESS_배틀_없으면_브로드캐스트_안함`() {
        every { battleRepository.findAllByStatus(BattleStatus.IN_PROGRESS) } returns emptyList()

        scheduler.broadcastRankings()

        verify(exactly = 0) { messagingTemplate.convertAndSend(any<String>(), any<Any>()) }
    }

    @Test
    fun `참가자_평가금액_내림차순_정렬_확인`() {
        val battle = createInProgressBattle(seedMoney = 10_000_000L)
        val userId1 = 1L
        val userId2 = 2L

        every { battleRepository.findAllByStatus(BattleStatus.IN_PROGRESS) } returns listOf(battle)
        every { battleSessionRepository.findParticipantIdsByBattleId(battle.battleId) } returns listOf(userId1, userId2)
        every { userRepository.findAllById(any<Iterable<Long>>()) } returns listOf(
            createUser(userId1, balance = 7_000_000L),
            createUser(userId2, balance = 12_000_000L)
        )
        every { positionRepository.findAllByUserIdInAndStatus(any(), PositionStatus.OPEN) } returns emptyList()
        justRun { messagingTemplate.convertAndSend(any<String>(), any<Any>()) }

        scheduler.broadcastRankings()

        val messageSlot = slot<BattleWebSocketMessage>()
        verify { messagingTemplate.convertAndSend(any<String>(), capture(messageSlot)) }

        val rankings = messageSlot.captured.data.rankings!!
        assertThat(rankings).hasSize(2)
        assertThat(rankings[0].rank).isEqualTo(1)
        assertThat(rankings[0].currentValuation).isEqualTo(12_000_000L)
        assertThat(rankings[1].rank).isEqualTo(2)
        assertThat(rankings[1].currentValuation).isEqualTo(7_000_000L)
    }

    @Test
    fun `오픈포지션_없는_참가자는_잔고만_반영`() {
        val battle = createInProgressBattle(seedMoney = 10_000_000L)
        val userId = 1L
        val userBalance = 9_500_000L

        every { battleRepository.findAllByStatus(BattleStatus.IN_PROGRESS) } returns listOf(battle)
        every { battleSessionRepository.findParticipantIdsByBattleId(battle.battleId) } returns listOf(userId)
        every { userRepository.findAllById(any<Iterable<Long>>()) } returns listOf(
            createUser(userId, balance = userBalance)
        )
        every { positionRepository.findAllByUserIdInAndStatus(any(), PositionStatus.OPEN) } returns emptyList()
        justRun { messagingTemplate.convertAndSend(any<String>(), any<Any>()) }

        scheduler.broadcastRankings()

        val messageSlot = slot<BattleWebSocketMessage>()
        verify { messagingTemplate.convertAndSend(any<String>(), capture(messageSlot)) }

        val rankings = messageSlot.captured.data.rankings!!
        assertThat(rankings).hasSize(1)
        assertThat(rankings[0].currentValuation).isEqualTo(userBalance)
    }

    @Test
    fun `특정_배틀_실패해도_다른_배틀_계속_처리`() {
        val failingBattle = createInProgressBattle(seedMoney = 10_000_000L)
        val successBattle = createInProgressBattle(seedMoney = 10_000_000L)
        val userId = 1L

        every { battleRepository.findAllByStatus(BattleStatus.IN_PROGRESS) } returns listOf(failingBattle, successBattle)

        every { battleSessionRepository.findParticipantIdsByBattleId(failingBattle.battleId) } throws RuntimeException("DB 오류")

        every { battleSessionRepository.findParticipantIdsByBattleId(successBattle.battleId) } returns listOf(userId)
        every { userRepository.findAllById(any<Iterable<Long>>()) } returns listOf(
            createUser(userId, balance = 10_000_000L)
        )
        every { positionRepository.findAllByUserIdInAndStatus(any(), PositionStatus.OPEN) } returns emptyList()
        justRun { messagingTemplate.convertAndSend(any<String>(), any<Any>()) }

        scheduler.broadcastRankings()

        verify(exactly = 1) { messagingTemplate.convertAndSend(any<String>(), any<Any>()) }
    }

    @Test
    fun `오픈포지션_평가금액이_잔고에_합산됨`() {
        val battle = createInProgressBattle(seedMoney = 10_000_000L)
        val userId = 1L
        val userBalance = 5_000_000L

        val position = createPosition(
            userId = userId,
            ticker = "KRW-BTC",
            averagePrice = 100_000_000L,
            quantity = BigDecimal("0.1"),
            leverage = 2,
            margin = 5_000_000L
        )
        val ticker = createTickerResponse("KRW-BTC", tradePrice = 110_000_000.0)

        every { battleRepository.findAllByStatus(BattleStatus.IN_PROGRESS) } returns listOf(battle)
        every { battleSessionRepository.findParticipantIdsByBattleId(battle.battleId) } returns listOf(userId)
        every { userRepository.findAllById(any<Iterable<Long>>()) } returns listOf(
            createUser(userId, balance = userBalance)
        )
        every { positionRepository.findAllByUserIdInAndStatus(any(), PositionStatus.OPEN) } returns listOf(position)
        every { tickerRedisRepository.findByMarket("KRW-BTC") } returns ticker
        justRun { messagingTemplate.convertAndSend(any<String>(), any<Any>()) }

        scheduler.broadcastRankings()

        val messageSlot = slot<BattleWebSocketMessage>()
        verify { messagingTemplate.convertAndSend(any<String>(), capture(messageSlot)) }

        val rankings = messageSlot.captured.data.rankings!!
        val positionEvaluated = position.evaluatedValue(110_000_000L)
        assertThat(rankings[0].currentValuation).isEqualTo(userBalance + positionEvaluated)
    }

    private fun createInProgressBattle(seedMoney: Long): Battle {
        return Battle().apply {
            this.status = BattleStatus.IN_PROGRESS
            this.seedMoney = seedMoney
            this.startTime = Instant.now().minusSeconds(60)
        }
    }

    private fun createUser(id: Long, balance: Long): User {
        val user = User(
            email = "user$id@test.com",
            nickname = "user$id",
            provider = AuthProvider.KAKAO,
            providerId = "provider_$id",
            balance = balance
        )
        val field = User::class.java.getDeclaredField("id")
        field.isAccessible = true
        field.set(user, id)
        return user
    }

    private fun createPosition(
        userId: Long,
        ticker: String,
        averagePrice: Long,
        quantity: BigDecimal,
        leverage: Int,
        margin: Long
    ): Position {
        return Position(
            userId = userId,
            ticker = ticker,
            direction = OrderDirection.LONG,
            quantity = quantity,
            averagePrice = averagePrice,
            leverage = leverage,
            margin = margin,
            status = PositionStatus.OPEN
        )
    }

    private fun createTickerResponse(market: String, tradePrice: Double): TickerResponse {
        return TickerResponse(
            market = market,
            tradePrice = tradePrice,
            changeRate = 0.0,
            changePrice = 0.0,
            change = ChangeType.RISE,
            accTradeVolume24h = 0.0,
            accTradePrice24h = 0.0,
            highPrice = tradePrice,
            lowPrice = tradePrice
        )
    }
}
