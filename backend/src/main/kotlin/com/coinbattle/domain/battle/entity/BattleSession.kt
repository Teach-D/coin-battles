package com.coinbattle.domain.battle.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "battle_sessions")
class BattleSession {
    @Id
    @Column(name = "id", columnDefinition = "UUID")
    val id: UUID = UUID.randomUUID()

    @Column(name = "battle_id", nullable = false, columnDefinition = "UUID")
    var battleId: UUID = UUID.randomUUID()

    @Column(name = "participant_id", nullable = false)
    var participantId: Long = 0L

    @CreationTimestamp
    @Column(name = "joined_at", updatable = false)
    var joinedAt: Instant = Instant.now()
}
