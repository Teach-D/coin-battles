package com.coinbattle.domain.battle

import com.coinbattle.common.exception.CoinBattleException
import com.coinbattle.common.exception.ErrorCode
import com.coinbattle.domain.battle.entity.Battle
import com.coinbattle.domain.battle.entity.BattleSession
import com.coinbattle.domain.battle.enum.BattleStatus
import com.coinbattle.domain.battle.repository.BattleRepository
import com.coinbattle.domain.battle.repository.BattleSessionRepository
import com.coinbattle.domain.battle.service.BattleEndService
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
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.springframework.messaging.simp.SimpMessagingTemplate
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional
import java.util.UUID

@ExtendWith(MockKExtension::class)
class BattleEndServiceTest {

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

    lateinit var battleEndService: BattleEndService

    @BeforeEach
    fun setUp() {
        battleEndService = BattleEndService(
            battleRepository,
            battleSessionRepository,
            userRepository,
            positionRepository,
            tickerRedisRepository,
            messagingTemplate
        )
    }

    @Test
    fun `배틀_종료_시_참가자별_수익률_계산_정확도`() {
        val userId1 = 1L
        val userId2 = 2L

        val battle = createInProgressBattle(userId1, durationMinutes = 10)
        battle.startTime = Instant.now().minusSeconds(700)

        val session1 = createBattleSession(battle.battleId, userId1)
        val session2 = createBattleSession(battle.battleId, userId2)

        val user1 = createUser(userId1, balance = 5_000_000L)
        val user2 = createUser(userId2, balance = 8_000_000L)

        val position1 = createPosition(userId1, "KRW-BTC", 100_000L, BigDecimal("0.05"), 2, 5_000_000L)
        val ticker = mockk<com.coinbattle.domain.market.dto.response.TickerResponse> {
            every { tradePrice } returns 110_000.0
        }

        every { battleSessionRepository.findByBattleId(battle.battleId) } returns listOf(session1, session2)
        every { userRepository.findAllById(any<Iterable<Long>>()) } returns listOf(user1, user2)
        every { positionRepository.findByUserIdAndStatus(userId1, PositionStatus.OPEN) } returns listOf(position1)
        every { positionRepository.findByUserIdAndStatus(userId2, PositionStatus.OPEN) } returns emptyList()
        every { tickerRedisRepository.findByMarket("KRW-BTC") } returns ticker
        every { battleRepository.save(any()) } answers { firstArg() }
        every { battleSessionRepository.save(any()) } answers { firstArg() }
        justRun { messagingTemplate.convertAndSend(any<String>(), any<Any>()) }

        battleEndService.finishBattleInTransaction(battle, Instant.now())

        verify { battleSessionRepository.save(match { it.participantId == userId2 && it.finalValuation == 8_000_000L }) }
        verify { battleSessionRepository.save(match { it.participantId == userId1 && it.finalValuation != null }) }
    }

    @Test
    fun `배틀_종료_시_승자_결정`() {
        val userId1 = 1L
        val userId2 = 2L

        val battle = createInProgressBattle(userId1, durationMinutes = 10)
        battle.startTime = Instant.now().minusSeconds(700)

        val session1 = createBattleSession(battle.battleId, userId1)
        val session2 = createBattleSession(battle.battleId, userId2)

        val user1 = createUser(userId1, balance = 5_000_000L)
        val user2 = createUser(userId2, balance = 12_000_000L)

        every { battleSessionRepository.findByBattleId(battle.battleId) } returns listOf(session1, session2)
        every { userRepository.findAllById(any<Iterable<Long>>()) } returns listOf(user1, user2)
        every { positionRepository.findByUserIdAndStatus(userId1, PositionStatus.OPEN) } returns emptyList()
        every { positionRepository.findByUserIdAndStatus(userId2, PositionStatus.OPEN) } returns emptyList()
        every { battleRepository.save(any()) } answers { firstArg() }
        every { battleSessionRepository.save(any()) } answers { firstArg() }
        justRun { messagingTemplate.convertAndSend(any<String>(), any<Any>()) }

        battleEndService.finishBattleInTransaction(battle, Instant.now())

        verify {
            battleRepository.save(match { it.winnerId == userId2 && it.status == BattleStatus.FINISHED })
        }
    }

    @Test
    fun `배틀_결과_조회_미종료_시_예외`() {
        val battleId = UUID.randomUUID()
        val userId = 1L

        val battle = createInProgressBattle(userId, durationMinutes = 10)

        every { battleRepository.findById(battleId) } returns Optional.of(battle)

        assertThatThrownBy {
            battleEndService.getBattleResult(battleId, userId)
        }
            .isInstanceOf(CoinBattleException::class.java)
            .hasMessageContaining("아직 종료되지 않았습니다")
    }

    @Test
    fun `배틀_결과_조회_비참가자_접근_차단`() {
        val battleId = UUID.randomUUID()
        val hostId = 1L
        val outsiderId = 99L

        val battle = createFinishedBattle(hostId)

        every { battleRepository.findById(battleId) } returns Optional.of(battle)
        every { battleSessionRepository.findByBattleId(battleId) } returns listOf(
            createBattleSession(battleId, hostId)
        )
        every { userRepository.findAllById(any<Iterable<Long>>()) } returns listOf(createUser(hostId, balance = 10_000_000L))

        assertThatThrownBy {
            battleEndService.getBattleResult(battleId, outsiderId)
        }
            .isInstanceOf(CoinBattleException::class.java)
            .hasMessageContaining("참가자만 결과를 조회할 수 있습니다")
    }

    private fun createInProgressBattle(hostUserId: Long, durationMinutes: Int): Battle {
        return Battle().apply {
            this.hostUserId = hostUserId
            this.userId = hostUserId
            this.status = BattleStatus.IN_PROGRESS
            this.duration = durationMinutes
            this.startTime = Instant.now()
        }
    }

    private fun createFinishedBattle(hostUserId: Long): Battle {
        return Battle().apply {
            this.hostUserId = hostUserId
            this.userId = hostUserId
            this.status = BattleStatus.FINISHED
            this.duration = 10
            this.startTime = Instant.now().minusSeconds(700)
            this.endTime = Instant.now()
        }
    }

    private fun createBattleSession(battleId: UUID, participantId: Long): BattleSession {
        return BattleSession().apply {
            this.battleId = battleId
            this.participantId = participantId
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
}
