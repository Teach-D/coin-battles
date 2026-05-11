package com.coinbattle.domain.battle.scheduler

import com.coinbattle.domain.battle.dto.response.BattleMessageData
import com.coinbattle.domain.battle.dto.response.BattleMessageType
import com.coinbattle.domain.battle.dto.response.BattleRankEntry
import com.coinbattle.domain.battle.dto.response.BattleWebSocketMessage
import com.coinbattle.domain.battle.entity.Battle
import com.coinbattle.domain.battle.enum.BattleStatus
import com.coinbattle.domain.battle.repository.BattleRepository
import com.coinbattle.domain.battle.repository.BattleSessionRepository
import com.coinbattle.domain.market.repository.TickerRedisRepository
import com.coinbattle.domain.order.entity.PositionStatus
import com.coinbattle.domain.order.repository.PositionRepository
import com.coinbattle.domain.user.repository.UserRepository
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class BattleRankingScheduler(
    private val battleRepository: BattleRepository,
    private val battleSessionRepository: BattleSessionRepository,
    private val userRepository: UserRepository,
    private val positionRepository: PositionRepository,
    private val tickerRedisRepository: TickerRedisRepository,
    private val messagingTemplate: SimpMessagingTemplate
) {
    @Scheduled(fixedDelay = 5_000)
    @Transactional(readOnly = true)
    fun broadcastRankings() {
        val activeBattles = battleRepository.findAllByStatus(BattleStatus.IN_PROGRESS)
        activeBattles.forEach { battle ->
            runCatching { broadcastBattleRanking(battle) }
        }
    }

    private fun broadcastBattleRanking(battle: Battle) {
        val participantIds = battleSessionRepository.findParticipantIdsByBattleId(battle.battleId)
        if (participantIds.isEmpty()) return

        val users = userRepository.findAllById(participantIds).associateBy { it.id }
        val openPositions = positionRepository
            .findAllByUserIdInAndStatus(participantIds, PositionStatus.OPEN)
            .groupBy { it.userId }

        val tickers = openPositions.values.flatten().map { it.ticker }.distinct()
        val priceMap = tickers.associate { ticker ->
            ticker to (tickerRedisRepository.findByMarket(ticker)?.tradePrice?.toLong() ?: 0L)
        }

        val rankings = participantIds.mapNotNull { userId ->
            val user = users[userId] ?: return@mapNotNull null
            val positions = openPositions[userId] ?: emptyList()
            val positionsValue = positions.sumOf { pos ->
                val price = priceMap[pos.ticker] ?: 0L
                if (price > 0) pos.evaluatedValue(price) else 0L
            }
            val finalValuation = user.balance + positionsValue
            val returnRate = (finalValuation - battle.seedMoney).toDouble() / battle.seedMoney * 100
            Triple(userId, user.nickname, finalValuation to returnRate)
        }.sortedByDescending { it.third.first }

        val rankEntries = rankings.mapIndexed { index, (userId, nickname, valuationPair) ->
            BattleRankEntry(
                rank = index + 1,
                userId = userId,
                nickname = nickname,
                returnRate = Math.round(valuationPair.second * 100.0) / 100.0,
                currentValuation = valuationPair.first
            )
        }

        messagingTemplate.convertAndSend(
            "/topic/battle/${battle.battleId}",
            BattleWebSocketMessage(
                type = BattleMessageType.RANK_UPDATE,
                battleId = battle.battleId.toString(),
                data = BattleMessageData(rankings = rankEntries)
            )
        )
    }
}
