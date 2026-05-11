package com.coinbattle.domain.battle.service

import com.coinbattle.common.exception.CoinBattleException
import com.coinbattle.common.exception.ErrorCode
import com.coinbattle.domain.battle.dto.response.BattleMessageData
import com.coinbattle.domain.battle.dto.response.BattleMessageType
import com.coinbattle.domain.battle.dto.response.BattleRankEntry
import com.coinbattle.domain.battle.dto.response.BattleResultResponse
import com.coinbattle.domain.battle.dto.response.BattleWebSocketMessage
import com.coinbattle.domain.battle.dto.response.ParticipantResultResponse
import com.coinbattle.domain.battle.entity.Battle
import com.coinbattle.domain.battle.entity.BattleSession
import com.coinbattle.domain.battle.enum.BattleStatus
import com.coinbattle.domain.battle.repository.BattleRepository
import com.coinbattle.domain.battle.repository.BattleSessionRepository
import com.coinbattle.domain.market.repository.TickerRedisRepository
import com.coinbattle.domain.order.entity.PositionStatus
import com.coinbattle.domain.order.repository.PositionRepository
import com.coinbattle.domain.user.entity.User
import com.coinbattle.domain.user.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class BattleEndService(
    private val battleRepository: BattleRepository,
    private val battleSessionRepository: BattleSessionRepository,
    private val userRepository: UserRepository,
    private val positionRepository: PositionRepository,
    private val tickerRedisRepository: TickerRedisRepository,
    private val messagingTemplate: SimpMessagingTemplate
) {
    @Autowired
    @Lazy
    private lateinit var self: BattleEndService

    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 30_000)
    fun processExpiredBattles() {
        val now = Instant.now()
        val minDurationExpiredBefore = now.minus(10, ChronoUnit.MINUTES)
        val expiredBattles = battleRepository.findExpiredBattles(BattleStatus.IN_PROGRESS, minDurationExpiredBefore)

        expiredBattles.forEach { battle ->
            try {
                self.finishBattleInTransaction(battle, now)
            } catch (e: Exception) {
                logger.error("배틀 종료 처리 실패 battleId=${battle.battleId}", e)
            }
        }
    }

    @Transactional
    fun finishBattleInTransaction(battle: Battle, now: Instant) {
        val expiredBefore = now.minus(battle.duration.toLong(), ChronoUnit.MINUTES)
        val startTime = battle.startTime ?: return
        if (startTime.isAfter(expiredBefore)) return

        val sessions = battleSessionRepository.findByBattleId(battle.battleId)
        val participantIds = sessions.map { it.participantId }
        val userMap = userRepository.findAllById(participantIds).associateBy { it.id }

        val valuations = sessions.map { session ->
            val user = userMap[session.participantId]
                ?: return@map SessionValuation(session, 0L)
            val finalValuation = calculateFinalValuation(user)
            SessionValuation(session, finalValuation)
        }

        val ranked = valuations.sortedByDescending { it.finalValuation }

        ranked.forEachIndexed { index, sv ->
            sv.session.finalValuation = sv.finalValuation
            sv.session.rank = index + 1
            battleSessionRepository.save(sv.session)
        }

        val winnerId = ranked.firstOrNull()?.session?.participantId
        battle.finish(winnerId)
        battleRepository.save(battle)

        broadcastBattleFinished(battle, ranked, userMap)
    }

    @Transactional(readOnly = true)
    fun getBattleResult(battleId: UUID, currentUserId: Long): BattleResultResponse {
        val battle = battleRepository.findById(battleId).orElseThrow {
            CoinBattleException(ErrorCode.BATTLE_NOT_FOUND)
        }

        if (battle.status != BattleStatus.FINISHED) {
            throw CoinBattleException(ErrorCode.BATTLE_NOT_FINISHED)
        }

        val sessions = battleSessionRepository.findByBattleId(battleId)
        val participantIds = sessions.map { it.participantId }

        if (currentUserId !in participantIds) {
            throw CoinBattleException(ErrorCode.BATTLE_ACCESS_DENIED)
        }

        val userMap = userRepository.findAllById(participantIds).associateBy { it.id }

        val participants = sessions.map { session ->
            val user = userMap[session.participantId]
            val finalValuation = session.finalValuation ?: 0L
            val profitAmount = finalValuation - battle.seedMoney
            val profitRate = if (battle.seedMoney > 0) {
                profitAmount.toDouble() / battle.seedMoney * 100.0
            } else 0.0

            ParticipantResultResponse(
                userId = session.participantId,
                nickname = user?.nickname ?: "",
                rank = session.rank ?: 0,
                isWinner = session.participantId == battle.winnerId,
                initialSeed = battle.seedMoney,
                finalValuation = finalValuation,
                profitAmount = profitAmount,
                profitRate = profitRate
            )
        }.sortedBy { it.rank }

        val myResult = participants.find { it.userId == currentUserId }

        return BattleResultResponse(
            battleId = battleId.toString(),
            status = battle.status.name,
            durationMinutes = battle.duration,
            endedAt = battle.endTime?.toString(),
            participants = participants,
            myResult = myResult
        )
    }

    private fun calculateFinalValuation(user: User): Long {
        val openPositions = positionRepository.findByUserIdAndStatus(user.id, PositionStatus.OPEN)
        val positionValue = openPositions.sumOf { position ->
            val currentPrice = tickerRedisRepository.findByMarket(position.ticker)?.tradePrice?.toLong()
                ?: position.averagePrice
            position.evaluatedValue(currentPrice)
        }
        return user.balance + positionValue
    }

    private fun broadcastBattleFinished(
        battle: Battle,
        ranked: List<SessionValuation>,
        userMap: Map<Long, User>
    ) {
        val rankings = ranked.mapIndexed { index, sv ->
            val user = userMap[sv.session.participantId]
            val returnRate = if (battle.seedMoney > 0) {
                (sv.finalValuation - battle.seedMoney).toDouble() / battle.seedMoney * 100.0
            } else 0.0
            BattleRankEntry(
                rank = index + 1,
                userId = sv.session.participantId,
                nickname = user?.nickname ?: "",
                returnRate = returnRate,
                currentValuation = sv.finalValuation
            )
        }

        messagingTemplate.convertAndSend(
            "/topic/battle/${battle.battleId}",
            BattleWebSocketMessage(
                type = BattleMessageType.BATTLE_FINISHED,
                battleId = battle.battleId.toString(),
                data = BattleMessageData(
                    rankings = rankings,
                    winnerId = battle.winnerId
                )
            )
        )
    }

    private data class SessionValuation(
        val session: BattleSession,
        val finalValuation: Long
    )
}
