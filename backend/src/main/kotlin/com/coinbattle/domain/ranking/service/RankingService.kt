package com.coinbattle.domain.ranking.service

import com.coinbattle.common.exception.CoinBattleException
import com.coinbattle.common.exception.ErrorCode
import com.coinbattle.domain.ranking.dto.response.MyRankingResponse
import com.coinbattle.domain.ranking.dto.response.PvpRankingEntryResponse
import com.coinbattle.domain.ranking.dto.response.RankingEntryResponse
import com.coinbattle.domain.user.repository.UserRepository
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RankingService(
    private val stringRedisTemplate: StringRedisTemplate,
    private val userRepository: UserRepository
) {
    companion object {
        const val SEASON_KEY = "leaderboard:season"
        const val DAILY_KEY = "leaderboard:daily"
        const val PVP_WINRATE_KEY = "leaderboard:pvp-winrate"
        const val PVP_STATS_KEY_PREFIX = "pvp:stats:"
        const val MAX_LIMIT = 100
    }

    fun updateRanking(userId: Long, evaluatedValue: Long) {
        val score = evaluatedValue.toDouble()
        stringRedisTemplate.opsForZSet().add(SEASON_KEY, userId.toString(), score)
        stringRedisTemplate.opsForZSet().add(DAILY_KEY, userId.toString(), score)
    }

    @Transactional(readOnly = true)
    fun getTopRankings(key: String, limit: Int): List<RankingEntryResponse> {
        val effectiveLimit = limit.coerceIn(1, MAX_LIMIT)
        val entries = stringRedisTemplate.opsForZSet()
            .reverseRangeWithScores(key, 0, effectiveLimit.toLong() - 1)
            ?: return emptyList()

        val userIds = entries.mapNotNull { it.value?.toLongOrNull() }
        val userMap = userRepository.findAllById(userIds).associateBy { it.id }

        return entries.mapIndexedNotNull { index, entry ->
            val userId = entry.value?.toLongOrNull() ?: return@mapIndexedNotNull null
            val user = userMap[userId] ?: return@mapIndexedNotNull null
            RankingEntryResponse(
                rank = index + 1,
                userId = userId,
                nickname = user.nickname,
                evaluatedValue = entry.score?.toLong() ?: 0L
            )
        }
    }

    @Transactional(readOnly = true)
    fun getMyRanking(userId: Long): MyRankingResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { CoinBattleException(ErrorCode.USER_NOT_FOUND) }

        val userIdStr = userId.toString()

        val seasonScore = stringRedisTemplate.opsForZSet().score(SEASON_KEY, userIdStr)
        val seasonRank = seasonScore?.let {
            stringRedisTemplate.opsForZSet().reverseRank(SEASON_KEY, userIdStr)?.let { rank -> rank + 1 }
        }

        val dailyScore = stringRedisTemplate.opsForZSet().score(DAILY_KEY, userIdStr)
        val dailyRank = dailyScore?.let {
            stringRedisTemplate.opsForZSet().reverseRank(DAILY_KEY, userIdStr)?.let { rank -> rank + 1 }
        }

        return MyRankingResponse(
            userId = userId,
            nickname = user.nickname,
            season = MyRankingResponse.RankingSlot(
                rank = seasonRank,
                evaluatedValue = seasonScore?.toLong() ?: 0L
            ),
            daily = MyRankingResponse.RankingSlot(
                rank = dailyRank,
                evaluatedValue = dailyScore?.toLong() ?: 0L
            )
        )
    }

    fun updatePvpWinRate(userId: Long, isWin: Boolean) {
        val statsKey = "$PVP_STATS_KEY_PREFIX$userId"
        stringRedisTemplate.opsForHash<String, String>().increment(statsKey, "total", 1L)
        if (isWin) {
            stringRedisTemplate.opsForHash<String, String>().increment(statsKey, "wins", 1L)
        }
        val wins = stringRedisTemplate.opsForHash<String, String>().get(statsKey, "wins")?.toLongOrNull() ?: 0L
        val total = stringRedisTemplate.opsForHash<String, String>().get(statsKey, "total")?.toLongOrNull() ?: 0L
        if (total == 0L) return
        val score = wins.toDouble() / total.toDouble() * 100.0
        stringRedisTemplate.opsForZSet().add(PVP_WINRATE_KEY, userId.toString(), score)
    }

    @Transactional(readOnly = true)
    fun getTopPvpRankings(limit: Int): List<PvpRankingEntryResponse> {
        val effectiveLimit = limit.coerceIn(1, MAX_LIMIT)
        val entries = stringRedisTemplate.opsForZSet()
            .reverseRangeWithScores(PVP_WINRATE_KEY, 0, effectiveLimit.toLong() - 1)
            ?: return emptyList()

        val userIds = entries.mapNotNull { it.value?.toLongOrNull() }
        val userMap = userRepository.findAllById(userIds).associateBy { it.id }

        return entries.mapIndexedNotNull { index, entry ->
            val uid = entry.value?.toLongOrNull() ?: return@mapIndexedNotNull null
            val user = userMap[uid] ?: return@mapIndexedNotNull null
            PvpRankingEntryResponse(
                rank = index + 1,
                userId = uid,
                nickname = user.nickname,
                winRatePct = entry.score ?: 0.0
            )
        }
    }

    fun resetDailyRanking() {
        stringRedisTemplate.delete(DAILY_KEY)
    }
}
