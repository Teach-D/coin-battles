package com.coinbattle.domain.battle.repository

import com.coinbattle.domain.battle.entity.Battle
import com.coinbattle.domain.battle.enum.BattleStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.*

interface BattleRepository : JpaRepository<Battle, UUID> {

    @Query("SELECT b FROM Battle b WHERE b.status = :status ORDER BY b.createdAt DESC")
    fun findByStatus(@Param("status") status: BattleStatus, pageable: Pageable): Page<Battle>

    @Query("""
        SELECT b FROM Battle b
        WHERE b.status = :status
        AND b.currentParticipants < b.maxParticipants
        ORDER BY b.createdAt ASC
    """)
    fun findJoinable(@Param("status") status: BattleStatus, pageable: Pageable): Page<Battle>

    @Query("""
        SELECT b FROM Battle b
        WHERE b.status = :status
        AND b.leverage = :leverage
        AND b.seedMoney = :seedMoney
        AND b.duration = :duration
        AND b.maxParticipants = :maxParticipants
        AND b.currentParticipants < b.maxParticipants
        ORDER BY b.createdAt ASC
    """)
    fun findMatchingBattle(
        @Param("status") status: BattleStatus,
        @Param("leverage") leverage: Int,
        @Param("seedMoney") seedMoney: Long,
        @Param("duration") duration: Int,
        @Param("maxParticipants") maxParticipants: Int,
        pageable: Pageable
    ): Page<Battle>

    @Query("SELECT b FROM Battle b WHERE b.status = :status AND b.startTime IS NOT NULL AND b.startTime <= :expiredBefore")
    fun findExpiredBattles(
        @Param("status") status: BattleStatus,
        @Param("expiredBefore") expiredBefore: Instant
    ): List<Battle>

    fun findAllByStatus(status: BattleStatus): List<Battle>
}
