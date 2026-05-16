package com.coinbattle.domain.battle.event

import com.coinbattle.domain.battle.dto.response.BattleMessageData
import com.coinbattle.domain.battle.dto.response.BattleMessageType
import com.coinbattle.domain.battle.dto.response.BattleWebSocketMessage
import com.coinbattle.domain.battle.service.BattleCardPipelineService
import org.slf4j.LoggerFactory
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class BattleFinishedEventListener(
    private val messagingTemplate: SimpMessagingTemplate,
    private val battleCardPipelineService: BattleCardPipelineService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onBattleFinished(event: BattleFinishedEvent) {
        try {
            messagingTemplate.convertAndSend(
                "/topic/battle/${event.battleId}",
                BattleWebSocketMessage(
                    type = BattleMessageType.BATTLE_FINISHED,
                    battleId = event.battleId,
                    data = BattleMessageData(
                        rankings = event.rankings,
                        winnerId = event.winnerId
                    )
                )
            )
            battleCardPipelineService.generateAndBroadcastCard(event.battleResult)
        } catch (e: Exception) {
            logger.error("배틀 종료 후처리 실패 battleId=${event.battleId}", e)
        }
    }
}
