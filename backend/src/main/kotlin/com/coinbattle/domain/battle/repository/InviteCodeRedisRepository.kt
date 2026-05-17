package com.coinbattle.domain.battle.repository

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import java.time.Duration
import java.util.UUID

@Repository
class InviteCodeRedisRepository(
    private val stringRedisTemplate: StringRedisTemplate
) {
    private val ttl = Duration.ofMinutes(10)

    fun save(inviteCode: String, battleId: UUID) {
        stringRedisTemplate.opsForValue().set(
            "battle:invite:$inviteCode",
            battleId.toString(),
            ttl
        )
    }

    fun findBattleId(inviteCode: String): String? =
        stringRedisTemplate.opsForValue().get("battle:invite:$inviteCode")
}
