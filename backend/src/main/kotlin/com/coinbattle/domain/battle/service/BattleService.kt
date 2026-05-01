package com.coinbattle.domain.battle.service

import com.coinbattle.common.exception.CoinBattleException
import com.coinbattle.common.exception.ErrorCode
import com.coinbattle.domain.battle.dto.request.CreateBattleRequest
import com.coinbattle.domain.battle.dto.response.BattleListResponse
import com.coinbattle.domain.battle.dto.response.BattleMessageData
import com.coinbattle.domain.battle.dto.response.BattleMessageType
import com.coinbattle.domain.battle.dto.response.BattleResponse
import com.coinbattle.domain.battle.dto.response.BattleSummary
import com.coinbattle.domain.battle.dto.response.BattleWebSocketMessage
import com.coinbattle.domain.battle.dto.response.JoinBattleResponse
import com.coinbattle.domain.battle.dto.response.ParticipantInfo
import com.coinbattle.domain.battle.entity.Battle
import com.coinbattle.domain.battle.entity.BattleSession
import com.coinbattle.domain.battle.enum.BattleStatus
import com.coinbattle.domain.battle.repository.BattleRepository
import com.coinbattle.domain.battle.repository.BattleSessionRepository
import com.coinbattle.domain.user.repository.UserRepository
import org.redisson.api.RedissonClient
import org.springframework.data.domain.PageRequest
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import java.util.concurrent.TimeUnit

@Service
class BattleService(
    private val battleRepository: BattleRepository,
    private val battleSessionRepository: BattleSessionRepository,
    private val userRepository: UserRepository,
    private val redissonClient: RedissonClient,
    private val messagingTemplate: SimpMessagingTemplate
) {
    private val allowedDurations = setOf(10, 30, 60)
    private val allowedMaxParticipants = setOf(2, 3, 5)

    @Transactional
    fun createBattle(userId: Long, request: CreateBattleRequest): BattleResponse {
        validateCreateRequest(request)

        val activeStatuses = listOf(BattleStatus.WAITING, BattleStatus.IN_PROGRESS)
        if (battleSessionRepository.existsActiveByParticipantId(userId, activeStatuses)) {
            throw CoinBattleException(ErrorCode.ALREADY_IN_BATTLE)
        }

        val battle = Battle().apply {
            hostUserId = userId
            this.userId = userId
            leverage = request.leverage
            seedMoney = request.seedMoney
            duration = request.duration
            maxParticipants = request.maxParticipants
            currentParticipants = 1
        }

        battleRepository.save(battle)

        battleSessionRepository.save(
            BattleSession().apply {
                battleId = battle.battleId
                participantId = userId
            }
        )

        return BattleResponse.from(battle)
    }

    @Transactional
    fun joinBattle(userId: Long, battleId: UUID): JoinBattleResponse {
        val lock = redissonClient.getLock("battle:${battleId}:join")
        if (!lock.tryLock(0, 3, TimeUnit.SECONDS)) {
            throw CoinBattleException(ErrorCode.BATTLE_LOCK_TIMEOUT)
        }

        try {
            return executeJoinBattle(userId, battleId)
        } finally {
            if (lock.isHeldByCurrentThread) lock.unlock()
        }
    }

    @Transactional
    fun executeJoinBattle(userId: Long, battleId: UUID): JoinBattleResponse {
        val alreadyInThisBattle = battleSessionRepository.existsByParticipantIdAndBattleId(userId, battleId)
        val activeStatuses = listOf(BattleStatus.WAITING, BattleStatus.IN_PROGRESS)

        if (!alreadyInThisBattle && battleSessionRepository.existsActiveByParticipantId(userId, activeStatuses)) {
            throw CoinBattleException(ErrorCode.ALREADY_IN_BATTLE)
        }

        val battle = battleRepository.findById(battleId).orElseThrow {
            CoinBattleException(ErrorCode.BATTLE_NOT_FOUND)
        }

        if (battle.status == BattleStatus.IN_PROGRESS || battle.status == BattleStatus.FINISHED) {
            throw CoinBattleException(ErrorCode.BATTLE_ALREADY_STARTED)
        }

        if (!battle.canAddParticipant() && !alreadyInThisBattle) {
            throw CoinBattleException(ErrorCode.BATTLE_FULL)
        }

        if (!alreadyInThisBattle) {
            battle.addParticipant()
            battleSessionRepository.save(
                BattleSession().apply {
                    this.battleId = battleId
                    participantId = userId
                }
            )
        }

        if (battle.canStart()) {
            battle.start()
            broadcastBattleStarted(battle)
        } else {
            broadcastParticipantJoined(battle, userId)
        }

        return JoinBattleResponse.from(battle)
    }

    @Transactional(readOnly = true)
    fun getBattle(battleId: UUID): BattleResponse {
        val battle = battleRepository.findById(battleId).orElseThrow {
            CoinBattleException(ErrorCode.BATTLE_NOT_FOUND)
        }

        val participantIds = battleSessionRepository.findParticipantIdsByBattleId(battleId)
        val userMap = userRepository.findAllById(participantIds).associateBy { it.id }

        val participants = participantIds.map { uid ->
            val user = userMap[uid]
            ParticipantInfo(
                userId = uid,
                nickname = user?.nickname ?: "",
                seedPriceSnapshot = null,
                currentValuation = null,
                returnRate = null
            )
        }

        return BattleResponse.from(battle, participants)
    }

    @Transactional(readOnly = true)
    fun getBattleList(status: BattleStatus, page: Int, size: Int): BattleListResponse {
        val pageable = PageRequest.of(page, size)
        val battlePage = battleRepository.findByStatus(status, pageable)

        return BattleListResponse(
            content = battlePage.content.map { BattleSummary.from(it) },
            totalElements = battlePage.totalElements,
            totalPages = battlePage.totalPages,
            page = page,
            size = size
        )
    }

    private fun validateCreateRequest(request: CreateBattleRequest) {
        if (request.duration !in allowedDurations) {
            throw CoinBattleException(ErrorCode.INVALID_DURATION)
        }
        if (request.maxParticipants !in allowedMaxParticipants) {
            throw CoinBattleException(ErrorCode.INVALID_MAX_PARTICIPANTS)
        }
    }

    private fun broadcastParticipantJoined(battle: Battle, userId: Long) {
        messagingTemplate.convertAndSend(
            "/topic/battle/${battle.battleId}",
            BattleWebSocketMessage(
                type = BattleMessageType.PARTICIPANT_JOINED,
                battleId = battle.battleId.toString(),
                data = BattleMessageData(
                    userId = userId,
                    currentParticipants = battle.currentParticipants
                )
            )
        )
    }

    private fun broadcastBattleStarted(battle: Battle) {
        messagingTemplate.convertAndSend(
            "/topic/battle/${battle.battleId}",
            BattleWebSocketMessage(
                type = BattleMessageType.BATTLE_STARTED,
                battleId = battle.battleId.toString(),
                data = BattleMessageData(
                    currentParticipants = battle.currentParticipants
                )
            )
        )
    }
}
