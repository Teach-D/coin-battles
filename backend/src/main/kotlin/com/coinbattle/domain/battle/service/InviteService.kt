package com.coinbattle.domain.battle.service

import com.coinbattle.common.exception.CoinBattleException
import com.coinbattle.common.exception.ErrorCode
import com.coinbattle.domain.battle.dto.response.InviteCodeResponse
import com.coinbattle.domain.battle.dto.response.JoinByInviteResponse
import com.coinbattle.domain.battle.entity.BattleSession
import com.coinbattle.domain.battle.enum.BattleStatus
import com.coinbattle.domain.battle.repository.BattleRepository
import com.coinbattle.domain.battle.repository.BattleSessionRepository
import com.coinbattle.domain.battle.repository.InviteCodeRedisRepository
import org.redisson.api.RedissonClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

@Service
class InviteService(
    private val battleRepository: BattleRepository,
    private val battleSessionRepository: BattleSessionRepository,
    private val inviteCodeRedisRepository: InviteCodeRedisRepository,
    private val redissonClient: RedissonClient,
    @Value("\${app.base-url:http://localhost:5173}") private val appBaseUrl: String = "http://localhost:5173"
) {

    @Transactional(readOnly = true)
    fun generateInviteCode(battleId: UUID, requesterId: Long): InviteCodeResponse {
        val battle = battleRepository.findById(battleId)
            .orElseThrow { CoinBattleException(ErrorCode.BATTLE_NOT_FOUND) }

        if (!battleSessionRepository.existsByParticipantIdAndBattleId(requesterId, battleId)) {
            throw CoinBattleException(ErrorCode.BATTLE_ACCESS_DENIED)
        }

        if (!battle.canGenerateInvite()) {
            throw CoinBattleException(ErrorCode.BATTLE_NOT_IN_PROGRESS)
        }

        val inviteCode = UUID.randomUUID().toString()
        inviteCodeRedisRepository.save(inviteCode, battleId)

        return InviteCodeResponse(
            inviteCode = inviteCode,
            inviteUrl = "$appBaseUrl/join/$inviteCode",
            expiresAt = Instant.now().plusSeconds(600)
        )
    }

    fun joinByInvite(inviteCode: String, inviteeId: Long): JoinByInviteResponse {
        val battleIdStr = inviteCodeRedisRepository.findBattleId(inviteCode)
            ?: throw CoinBattleException(ErrorCode.INVITE_CODE_NOT_FOUND)

        val battleId = UUID.fromString(battleIdStr)
        val lock = redissonClient.getLock("battle:${battleId}:join")
        val acquired = lock.tryLock(3L, 3L, TimeUnit.SECONDS)

        if (!acquired) {
            throw CoinBattleException(ErrorCode.BATTLE_LOCK_TIMEOUT)
        }

        try {
            return executeJoinByInvite(battleId, inviteeId)
        } finally {
            if (acquired) lock.unlock()
        }
    }

    @Transactional
    fun executeJoinByInvite(battleId: UUID, inviteeId: Long): JoinByInviteResponse {
        val battle = battleRepository.findById(battleId)
            .orElseThrow { CoinBattleException(ErrorCode.BATTLE_NOT_FOUND) }

        if (battle.status == BattleStatus.FINISHED) {
            throw CoinBattleException(ErrorCode.BATTLE_ALREADY_FINISHED)
        }

        if (battleSessionRepository.existsByParticipantIdAndBattleId(inviteeId, battleId)) {
            throw CoinBattleException(ErrorCode.ALREADY_JOINED_BATTLE)
        }

        battle.addLateParticipant()
        battleRepository.save(battle)

        battleSessionRepository.save(
            BattleSession().apply {
                this.battleId = battleId
                this.participantId = inviteeId
            }
        )

        return JoinByInviteResponse(
            battleId = battleId.toString(),
            battleRoomUrl = "/battles/$battleId",
            joinedAt = Instant.now()
        )
    }
}
