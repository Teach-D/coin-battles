package com.coinbattle.domain.battle.entity

import com.coinbattle.domain.battle.enum.BattleStatus
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "battles")
class Battle {
    @Id
    @Column(name = "battle_id", columnDefinition = "UUID")
    val battleId: UUID = UUID.randomUUID()

    @Column(name = "host_user_id", nullable = false)
    var hostUserId: Long = 0L

    @Column(name = "user_id", nullable = false)
    var userId: Long = 0L

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: BattleStatus = BattleStatus.WAITING

    @Column(name = "start_time")
    var startTime: Instant? = null

    @Column(name = "end_time")
    var endTime: Instant? = null

    @Column(name = "leverage", nullable = false)
    var leverage: Int = 2

    @Column(name = "seed_money", nullable = false)
    var seedMoney: Long = 10_000_000

    @Column(name = "duration", nullable = false)
    var duration: Int = 10

    @Column(name = "max_participants", nullable = false)
    var maxParticipants: Int = 2

    @Column(name = "current_participants", nullable = false)
    var currentParticipants: Int = 0

    @Column(name = "winner_id")
    var winnerId: Long? = null

    @Version
    @Column(name = "version")
    var version: Int = 0

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    var createdAt: Instant = Instant.now()

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: Instant = Instant.now()

    fun canAddParticipant(): Boolean = status == BattleStatus.WAITING && currentParticipants < maxParticipants

    fun canStart(): Boolean = status == BattleStatus.WAITING && currentParticipants >= maxParticipants

    fun start() {
        status = BattleStatus.IN_PROGRESS
        startTime = Instant.now()
    }

    fun finish(winnerId: Long? = null) {
        status = BattleStatus.FINISHED
        endTime = Instant.now()
        this.winnerId = winnerId
    }

    fun addParticipant() {
        currentParticipants++
    }
}
