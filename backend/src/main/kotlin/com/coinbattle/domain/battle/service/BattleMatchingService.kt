package com.coinbattle.domain.battle.service

import com.coinbattle.common.exception.CoinBattleException
import com.coinbattle.common.exception.ErrorCode
import com.coinbattle.domain.battle.dto.request.MatchBattleRequest
import com.coinbattle.domain.battle.dto.response.MatchFoundMessage
import com.coinbattle.domain.battle.dto.response.MatchParticipant
import com.coinbattle.domain.battle.dto.response.MatchQueueResponse
import com.coinbattle.domain.battle.entity.Battle
import com.coinbattle.domain.battle.entity.BattleSession
import com.coinbattle.domain.battle.repository.BattleRepository
import com.coinbattle.domain.battle.repository.BattleSessionRepository
import com.coinbattle.domain.user.repository.UserRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BattleMatchingService(
    private val stringRedisTemplate: StringRedisTemplate,
    private val battleRepository: BattleRepository,
    private val battleSessionRepository: BattleSessionRepository,
    private val userRepository: UserRepository,
    private val messagingTemplate: SimpMessagingTemplate,
    private val objectMapper: ObjectMapper
) {
    companion object {
        private const val ESTIMATED_WAIT_SECONDS = 30
    }

    fun joinMatchQueue(userId: Long, request: MatchBattleRequest): MatchQueueResponse {
        val queueKey = buildQueueKey(request)
        val userIdStr = userId.toString()

        val listOps = stringRedisTemplate.opsForList()
        val queueSize = listOps.size(queueKey) ?: 0

        val alreadyInQueue = (0 until queueSize).any { idx ->
            listOps.index(queueKey, idx) == userIdStr
        }

        if (!alreadyInQueue) {
            listOps.rightPush(queueKey, userIdStr)
        }

        val currentSize = listOps.size(queueKey) ?: 0

        if (currentSize >= request.maxParticipants) {
            tryCreateMatchFromQueue(queueKey, request)
        }

        return MatchQueueResponse(
            queueKey = queueKey,
            estimatedWaitSeconds = ESTIMATED_WAIT_SECONDS
        )
    }

    fun leaveMatchQueue(userId: Long) {
        val pattern = "battle:queue:*"
        val keys = stringRedisTemplate.keys(pattern)
        val userIdStr = userId.toString()

        var found = false
        keys.forEach { key ->
            val removed = stringRedisTemplate.opsForList().remove(key, 1, userIdStr)
            if ((removed ?: 0) > 0) {
                found = true
            }
        }

        if (!found) {
            throw CoinBattleException(ErrorCode.NOT_IN_MATCH_QUEUE)
        }
    }

    @Transactional
    fun tryCreateMatchFromQueue(queueKey: String, request: MatchBattleRequest) {
        val listOps = stringRedisTemplate.opsForList()
        val matchedUserIds = mutableListOf<Long>()

        repeat(request.maxParticipants) {
            val userIdStr = listOps.leftPop(queueKey) ?: return
            matchedUserIds.add(userIdStr.toLong())
        }

        if (matchedUserIds.size < request.maxParticipants) {
            matchedUserIds.forEach { uid ->
                listOps.leftPush(queueKey, uid.toString())
            }
            return
        }

        val battle = Battle().apply {
            hostUserId = matchedUserIds.first()
            userId = matchedUserIds.first()
            leverage = request.leverage
            seedMoney = request.seedMoney
            duration = request.duration
            maxParticipants = request.maxParticipants
            currentParticipants = matchedUserIds.size
        }

        battleRepository.save(battle)

        matchedUserIds.forEach { uid ->
            battleSessionRepository.save(
                BattleSession().apply {
                    battleId = battle.battleId
                    participantId = uid
                }
            )
        }

        val userMap = userRepository.findAllById(matchedUserIds).associateBy { it.id }
        val participants = matchedUserIds.map { uid ->
            MatchParticipant(
                userId = uid,
                nickname = userMap[uid]?.nickname ?: ""
            )
        }

        val message = MatchFoundMessage(
            battleId = battle.battleId.toString(),
            status = battle.status.name,
            participants = participants
        )

        matchedUserIds.forEach { uid ->
            messagingTemplate.convertAndSendToUser(
                uid.toString(),
                "/queue/battle/match",
                message
            )
        }
    }

    private fun buildQueueKey(request: MatchBattleRequest): String =
        "battle:queue:${request.leverage}:${request.seedMoney}:${request.duration}:${request.maxParticipants}"
}
