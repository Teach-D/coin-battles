package com.coinbattle.domain.battle

import com.coinbattle.common.exception.CoinBattleException
import com.coinbattle.common.exception.ErrorCode
import com.coinbattle.domain.battle.repository.BattleRepository
import com.coinbattle.domain.battle.repository.BattleSessionRepository
import com.coinbattle.domain.battle.repository.InviteCodeRedisRepository
import com.coinbattle.domain.battle.service.InviteService
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.redisson.api.RLock
import org.redisson.api.RedissonClient
import java.util.Optional
import java.util.UUID
import java.util.concurrent.TimeUnit

@ExtendWith(MockKExtension::class)
class InviteServiceTest {

    @MockK
    lateinit var battleRepository: BattleRepository

    @MockK
    lateinit var battleSessionRepository: BattleSessionRepository

    @MockK
    lateinit var inviteCodeRedisRepository: InviteCodeRedisRepository

    @MockK
    lateinit var redissonClient: RedissonClient

    lateinit var inviteService: InviteService

    @BeforeEach
    fun setUp() {
        inviteService = InviteService(
            battleRepository,
            battleSessionRepository,
            inviteCodeRedisRepository,
            redissonClient
        )
    }

    // ── 초대 코드 생성 ──────────────────────────────────────────────

    @Test
    fun `IN_PROGRESS_배틀_참가자가_초대코드_생성_성공`() {
        // given
        val battleId = UUID.randomUUID()
        val requesterId = 1L
        val battle = InviteFixture.createInProgressBattle(battleId = battleId)
        every { battleRepository.findById(battleId) } returns Optional.of(battle)
        every { battleSessionRepository.existsByParticipantIdAndBattleId(requesterId, battleId) } returns true
        justRun { inviteCodeRedisRepository.save(any(), battleId) }

        // when
        val result = inviteService.generateInviteCode(battleId, requesterId)

        // then
        assertThat(result.inviteCode).isNotBlank()
    }

    @Test
    fun `초대코드_생성시_inviteUrl_반환`() {
        // given
        val battleId = UUID.randomUUID()
        val requesterId = 1L
        val battle = InviteFixture.createInProgressBattle(battleId = battleId)
        every { battleRepository.findById(battleId) } returns Optional.of(battle)
        every { battleSessionRepository.existsByParticipantIdAndBattleId(requesterId, battleId) } returns true
        justRun { inviteCodeRedisRepository.save(any(), battleId) }

        // when
        val result = inviteService.generateInviteCode(battleId, requesterId)

        // then
        assertThat(result.inviteUrl).contains(result.inviteCode)
    }

    @Test
    fun `배틀_미존재시_초대코드_생성_CoinBattleException_발생`() {
        // given
        val battleId = UUID.randomUUID()
        every { battleRepository.findById(battleId) } returns Optional.empty()

        // when & then
        assertThatThrownBy { inviteService.generateInviteCode(battleId, 1L) }
            .isInstanceOf(CoinBattleException::class.java)
            .hasMessageContaining(ErrorCode.BATTLE_NOT_FOUND.message)
    }

    @Test
    fun `INV-01_WAITING_배틀에서_초대코드_생성시_CoinBattleException_발생`() {
        // given
        val battleId = UUID.randomUUID()
        val battle = InviteFixture.createWaitingBattle(battleId = battleId)
        every { battleRepository.findById(battleId) } returns Optional.of(battle)
        every { battleSessionRepository.existsByParticipantIdAndBattleId(any(), battleId) } returns true

        // when & then
        // ErrorCode.BATTLE_NOT_IN_PROGRESS — 신규 에러코드 (구현 시 추가 필요)
        assertThatThrownBy { inviteService.generateInviteCode(battleId, 1L) }
            .isInstanceOf(CoinBattleException::class.java)
            .hasMessageContaining("배틀이 진행 중이 아닙니다")
    }

    @Test
    fun `INV-01_FINISHED_배틀에서_초대코드_생성시_CoinBattleException_발생`() {
        // given
        val battleId = UUID.randomUUID()
        val battle = InviteFixture.createFinishedBattle(battleId = battleId)
        every { battleRepository.findById(battleId) } returns Optional.of(battle)
        every { battleSessionRepository.existsByParticipantIdAndBattleId(any(), battleId) } returns true

        // when & then
        // ErrorCode.BATTLE_NOT_IN_PROGRESS — 신규 에러코드 (구현 시 추가 필요)
        assertThatThrownBy { inviteService.generateInviteCode(battleId, 1L) }
            .isInstanceOf(CoinBattleException::class.java)
            .hasMessageContaining("배틀이 진행 중이 아닙니다")
    }

    @Test
    fun `INV-02_비참가자가_초대코드_생성시_CoinBattleException_발생`() {
        // given
        val battleId = UUID.randomUUID()
        val nonParticipantId = 99L
        val battle = InviteFixture.createInProgressBattle(battleId = battleId)
        every { battleRepository.findById(battleId) } returns Optional.of(battle)
        every { battleSessionRepository.existsByParticipantIdAndBattleId(nonParticipantId, battleId) } returns false

        // when & then
        assertThatThrownBy { inviteService.generateInviteCode(battleId, nonParticipantId) }
            .isInstanceOf(CoinBattleException::class.java)
            .hasMessageContaining(ErrorCode.BATTLE_ACCESS_DENIED.message)
    }

    // ── 초대 코드로 참가 ────────────────────────────────────────────

    @Test
    fun `유효한_초대코드로_IN_PROGRESS_배틀_참가_성공`() {
        // given
        val battleId = UUID.randomUUID()
        val inviteCode = InviteFixture.validInviteCode()
        val inviteeId = 2L
        val battle = InviteFixture.createInProgressBattle(battleId = battleId)
        val lock = mockk<RLock>()
        every { inviteCodeRedisRepository.findBattleId(inviteCode) } returns battleId.toString()
        every { battleRepository.findById(battleId) } returns Optional.of(battle)
        every { battleSessionRepository.existsByParticipantIdAndBattleId(inviteeId, battleId) } returns false
        every { redissonClient.getLock("battle:${battleId}:join") } returns lock
        every { lock.tryLock(3L, 3L, TimeUnit.SECONDS) } returns true
        every { battleSessionRepository.save(any()) } answers { firstArg() }
        every { battleRepository.save(any()) } answers { firstArg() }
        justRun { lock.unlock() }

        // when
        val result = inviteService.joinByInvite(inviteCode, inviteeId)

        // then
        assertThat(result.battleId).isEqualTo(battleId.toString())
    }

    @Test
    fun `초대코드_참가_성공시_battleRoomUrl_반환`() {
        // given
        val battleId = UUID.randomUUID()
        val inviteCode = InviteFixture.validInviteCode()
        val inviteeId = 2L
        val battle = InviteFixture.createInProgressBattle(battleId = battleId)
        val lock = mockk<RLock>()
        every { inviteCodeRedisRepository.findBattleId(inviteCode) } returns battleId.toString()
        every { battleRepository.findById(battleId) } returns Optional.of(battle)
        every { battleSessionRepository.existsByParticipantIdAndBattleId(inviteeId, battleId) } returns false
        every { redissonClient.getLock("battle:${battleId}:join") } returns lock
        every { lock.tryLock(3L, 3L, TimeUnit.SECONDS) } returns true
        every { battleSessionRepository.save(any()) } answers { firstArg() }
        every { battleRepository.save(any()) } answers { firstArg() }
        justRun { lock.unlock() }

        // when
        val result = inviteService.joinByInvite(inviteCode, inviteeId)

        // then
        assertThat(result.battleRoomUrl).contains(battleId.toString())
    }

    @Test
    fun `INV-03_만료된_초대코드로_참가시_CoinBattleException_발생`() {
        // given
        val expiredCode = InviteFixture.validInviteCode()
        every { inviteCodeRedisRepository.findBattleId(expiredCode) } returns null

        // when & then
        // ErrorCode.INVITE_CODE_NOT_FOUND — 신규 에러코드 (구현 시 추가 필요)
        assertThatThrownBy { inviteService.joinByInvite(expiredCode, 2L) }
            .isInstanceOf(CoinBattleException::class.java)
            .hasMessageContaining("초대 코드가 만료되었거나 존재하지 않습니다")
    }

    @Test
    fun `INV-04_이미_참가중인_배틀에_초대코드로_재참가시_CoinBattleException_발생`() {
        // given
        val battleId = UUID.randomUUID()
        val inviteCode = InviteFixture.validInviteCode()
        val alreadyJoinedUserId = 2L
        val battle = InviteFixture.createInProgressBattle(battleId = battleId)
        val lock = mockk<RLock>()
        every { inviteCodeRedisRepository.findBattleId(inviteCode) } returns battleId.toString()
        every { battleRepository.findById(battleId) } returns Optional.of(battle)
        every { battleSessionRepository.existsByParticipantIdAndBattleId(alreadyJoinedUserId, battleId) } returns true
        every { redissonClient.getLock("battle:${battleId}:join") } returns lock
        every { lock.tryLock(3L, 3L, TimeUnit.SECONDS) } returns true
        justRun { lock.unlock() }

        // when & then
        // ErrorCode.ALREADY_JOINED_BATTLE — 신규 에러코드 (구현 시 추가 필요)
        assertThatThrownBy { inviteService.joinByInvite(inviteCode, alreadyJoinedUserId) }
            .isInstanceOf(CoinBattleException::class.java)
            .hasMessageContaining("이미 이 배틀에 참가 중입니다")
    }

    @Test
    fun `INV-05_FINISHED_배틀_초대코드로_참가시_CoinBattleException_발생`() {
        // given
        val battleId = UUID.randomUUID()
        val inviteCode = InviteFixture.validInviteCode()
        val inviteeId = 2L
        val finishedBattle = InviteFixture.createFinishedBattle(battleId = battleId)
        val lock = mockk<RLock>()
        every { inviteCodeRedisRepository.findBattleId(inviteCode) } returns battleId.toString()
        every { battleRepository.findById(battleId) } returns Optional.of(finishedBattle)
        every { battleSessionRepository.existsByParticipantIdAndBattleId(inviteeId, battleId) } returns false
        every { redissonClient.getLock("battle:${battleId}:join") } returns lock
        every { lock.tryLock(3L, 3L, TimeUnit.SECONDS) } returns true
        justRun { lock.unlock() }

        // when & then
        // ErrorCode.BATTLE_ALREADY_FINISHED — 신규 에러코드 (구현 시 추가 필요)
        assertThatThrownBy { inviteService.joinByInvite(inviteCode, inviteeId) }
            .isInstanceOf(CoinBattleException::class.java)
            .hasMessageContaining("이미 종료된 배틀입니다")
    }

    @Test
    fun `락_획득_실패시_CoinBattleException_발생`() {
        // given
        val battleId = UUID.randomUUID()
        val inviteCode = InviteFixture.validInviteCode()
        val lock = mockk<RLock>()
        every { inviteCodeRedisRepository.findBattleId(inviteCode) } returns battleId.toString()
        every { battleRepository.findById(battleId) } returns Optional.of(InviteFixture.createInProgressBattle(battleId = battleId))
        every { battleSessionRepository.existsByParticipantIdAndBattleId(any(), battleId) } returns false
        every { redissonClient.getLock("battle:${battleId}:join") } returns lock
        every { lock.tryLock(3L, 3L, TimeUnit.SECONDS) } returns false

        // when & then
        assertThatThrownBy { inviteService.joinByInvite(inviteCode, 2L) }
            .isInstanceOf(CoinBattleException::class.java)
            .hasMessageContaining(ErrorCode.BATTLE_LOCK_TIMEOUT.message)
    }

    @Test
    fun `초대코드_참가_성공시_BattleSession_저장됨`() {
        // given
        val battleId = UUID.randomUUID()
        val inviteCode = InviteFixture.validInviteCode()
        val inviteeId = 2L
        val battle = InviteFixture.createInProgressBattle(battleId = battleId)
        val lock = mockk<RLock>()
        every { inviteCodeRedisRepository.findBattleId(inviteCode) } returns battleId.toString()
        every { battleRepository.findById(battleId) } returns Optional.of(battle)
        every { battleSessionRepository.existsByParticipantIdAndBattleId(inviteeId, battleId) } returns false
        every { redissonClient.getLock("battle:${battleId}:join") } returns lock
        every { lock.tryLock(3L, 3L, TimeUnit.SECONDS) } returns true
        every { battleSessionRepository.save(any()) } answers { firstArg() }
        every { battleRepository.save(any()) } answers { firstArg() }
        justRun { lock.unlock() }

        // when
        inviteService.joinByInvite(inviteCode, inviteeId)

        // then
        verify(exactly = 1) { battleSessionRepository.save(any()) }
    }

    @Test
    fun `초대코드_참가_성공시_락_반드시_해제됨`() {
        // given
        val battleId = UUID.randomUUID()
        val inviteCode = InviteFixture.validInviteCode()
        val inviteeId = 2L
        val battle = InviteFixture.createInProgressBattle(battleId = battleId)
        val lock = mockk<RLock>()
        every { inviteCodeRedisRepository.findBattleId(inviteCode) } returns battleId.toString()
        every { battleRepository.findById(battleId) } returns Optional.of(battle)
        every { battleSessionRepository.existsByParticipantIdAndBattleId(inviteeId, battleId) } returns false
        every { redissonClient.getLock("battle:${battleId}:join") } returns lock
        every { lock.tryLock(3L, 3L, TimeUnit.SECONDS) } returns true
        every { battleSessionRepository.save(any()) } answers { firstArg() }
        every { battleRepository.save(any()) } answers { firstArg() }
        justRun { lock.unlock() }

        // when
        inviteService.joinByInvite(inviteCode, inviteeId)

        // then
        verify(exactly = 1) { lock.unlock() }
    }
}
