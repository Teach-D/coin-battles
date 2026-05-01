package com.coinbattle.domain.battle.repository

import com.coinbattle.domain.battle.entity.BattleSession
import com.coinbattle.domain.battle.enum.BattleStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.*

interface BattleSessionRepository : JpaRepository<BattleSession, UUID> {

    @Query("SELECT bs.participantId FROM BattleSession bs WHERE bs.battleId = :battleId")
    fun findParticipantIdsByBattleId(@Param("battleId") battleId: UUID): List<Long>

    fun findByBattleId(battleId: UUID): List<BattleSession>

    @Query("""
        SELECT bs FROM BattleSession bs
        WHERE bs.participantId = :participantId
        AND bs.battleId = :battleId
    """)
    fun findByParticipantIdAndBattleId(
        @Param("participantId") participantId: Long,
        @Param("battleId") battleId: UUID
    ): Optional<BattleSession>

    @Query("""
        SELECT COUNT(bs) > 0 FROM BattleSession bs
        WHERE bs.participantId = :participantId
        AND bs.battleId = :battleId
    """)
    fun existsByParticipantIdAndBattleId(
        @Param("participantId") participantId: Long,
        @Param("battleId") battleId: UUID
    ): Boolean

    @Query("""
        SELECT COUNT(bs) > 0 FROM BattleSession bs
        WHERE bs.participantId = :participantId
        AND bs.battleId IN (
            SELECT b.battleId FROM Battle b
            WHERE b.status IN :activeStatuses
        )
    """)
    fun existsActiveByParticipantId(
        @Param("participantId") participantId: Long,
        @Param("activeStatuses") activeStatuses: List<BattleStatus>
    ): Boolean
}
