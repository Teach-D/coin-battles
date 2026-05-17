package com.coinbattle.domain.battle

import com.coinbattle.domain.battle.entity.Battle
import com.coinbattle.domain.battle.entity.BattleSession
import com.coinbattle.domain.battle.enum.BattleStatus
import java.time.LocalDateTime
import java.util.UUID

object InviteFixture {

    private fun setField(obj: Any, fieldName: String, value: Any?) {
        val field = generateSequence(obj::class.java) { it.superclass }
            .flatMap { it.declaredFields.asSequence() }
            .first { it.name == fieldName }
        field.isAccessible = true
        field.set(obj, value)
    }

    fun createInProgressBattle(
        battleId: UUID = UUID.randomUUID(),
        hostUserId: Long = 1L,
        seedMoney: Long = 1_000_000L,
        maxParticipants: Int = 5
    ): Battle {
        val battle = Battle()
        setField(battle, "battleId", battleId)
        setField(battle, "hostUserId", hostUserId)
        setField(battle, "seedMoney", seedMoney)
        setField(battle, "duration", 10)
        setField(battle, "maxParticipants", maxParticipants)
        setField(battle, "currentParticipants", 1)
        setField(battle, "status", BattleStatus.IN_PROGRESS)
        return battle
    }

    fun createWaitingBattle(
        battleId: UUID = UUID.randomUUID(),
        hostUserId: Long = 1L
    ): Battle {
        val battle = Battle()
        setField(battle, "battleId", battleId)
        setField(battle, "hostUserId", hostUserId)
        setField(battle, "seedMoney", 1_000_000L)
        setField(battle, "duration", 10)
        setField(battle, "maxParticipants", 5)
        setField(battle, "currentParticipants", 1)
        setField(battle, "status", BattleStatus.WAITING)
        return battle
    }

    fun createFinishedBattle(
        battleId: UUID = UUID.randomUUID(),
        hostUserId: Long = 1L
    ): Battle {
        val battle = Battle()
        setField(battle, "battleId", battleId)
        setField(battle, "hostUserId", hostUserId)
        setField(battle, "seedMoney", 1_000_000L)
        setField(battle, "duration", 10)
        setField(battle, "maxParticipants", 5)
        setField(battle, "currentParticipants", 1)
        setField(battle, "status", BattleStatus.FINISHED)
        return battle
    }

    fun createBattleSession(
        battleId: UUID = UUID.randomUUID(),
        participantId: Long = 1L,
        joinedAt: LocalDateTime = LocalDateTime.now()
    ): BattleSession {
        val session = BattleSession()
        setField(session, "battleId", battleId)
        setField(session, "participantId", participantId)
        setField(session, "joinedAt", joinedAt)
        return session
    }

    fun validInviteCode(): String = UUID.randomUUID().toString()
}
