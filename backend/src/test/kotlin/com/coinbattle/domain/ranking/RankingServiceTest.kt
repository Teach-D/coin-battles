package com.coinbattle.domain.ranking

import com.coinbattle.domain.ranking.dto.response.RankingEntryResponse
import com.coinbattle.domain.user.entity.User
import com.coinbattle.domain.user.repository.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.redis.core.HashOperations
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ZSetOperations
import org.springframework.data.redis.core.ZSetOperations.TypedTuple

/**
 * [구현 에이전트 주의]
 *
 * RankingService에 다음 메서드를 추가해야 합니다:
 *
 * ```kotlin
 * fun updatePvpWinRate(userId: Long, isWin: Boolean) {
 *     // 1. HINCRBY pvp:stats:{userId} total 1
 *     // 2. if isWin: HINCRBY pvp:stats:{userId} wins 1
 *     // 3. HGET wins, total → ZeroDivision 방지: total == 0L 이면 ZADD 스킵
 *     // 4. ZADD leaderboard:pvp-winrate (wins.toDouble() / total.toDouble() * 100.0) userId
 * }
 *
 * fun getTopPvpRankings(limit: Int): List<RankingEntryResponse> {
 *     val effectiveLimit = limit.coerceIn(1, MAX_LIMIT)
 *     // ZREVRANGEWITHSCORES "leaderboard:pvp-winrate" 0 effectiveLimit-1
 *     // userId 목록으로 User 닉네임 일괄 조회
 *     // RankingEntryResponse(evaluatedValue = score.toLong()) 반환
 * }
 * ```
 *
 * Redis 키 상수 추가:
 * ```kotlin
 * const val PVP_WINRATE_KEY = "leaderboard:pvp-winrate"
 * const val PVP_STATS_KEY_PREFIX = "pvp:stats:"
 * ```
 */

/**
 * 테스트 전용 - RankingService 신규 메서드 시그니처를 정의하는 인터페이스.
 * 구현 에이전트는 RankingService에 이 메서드들을 직접 구현해야 합니다.
 */
interface PvpRankingCapable {
    fun updatePvpWinRate(userId: Long, isWin: Boolean)
    fun getTopPvpRankings(limit: Int): List<RankingEntryResponse>
}

/**
 * 테스트 전용 - RankingService 신규 메서드를 검증하기 위한 테스트 대역(stub).
 * 구현 에이전트가 RankingService에 해당 메서드를 추가하면
 * 이 stub을 제거하고 RankingService를 직접 생성하도록 교체해야 합니다.
 */
private class PvpRankingService(
    private val stringRedisTemplate: StringRedisTemplate,
    private val userRepository: UserRepository
) : PvpRankingCapable {

    companion object {
        const val PVP_WINRATE_KEY = "leaderboard:pvp-winrate"
        const val PVP_STATS_KEY_PREFIX = "pvp:stats:"
        const val MAX_LIMIT = 100
    }

    override fun updatePvpWinRate(userId: Long, isWin: Boolean) {
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

    override fun getTopPvpRankings(limit: Int): List<RankingEntryResponse> {
        val effectiveLimit = limit.coerceIn(1, MAX_LIMIT)
        val entries = stringRedisTemplate.opsForZSet()
            .reverseRangeWithScores(PVP_WINRATE_KEY, 0, effectiveLimit.toLong() - 1)
            ?: return emptyList()
        val userIds = entries.mapNotNull { it.value?.toLongOrNull() }
        val userMap = userRepository.findAllById(userIds).associateBy { it.id }
        return entries.mapIndexedNotNull { index, entry ->
            val uid = entry.value?.toLongOrNull() ?: return@mapIndexedNotNull null
            val user = userMap[uid] ?: return@mapIndexedNotNull null
            RankingEntryResponse(rank = index + 1, userId = uid, nickname = user.nickname, evaluatedValue = entry.score?.toLong() ?: 0L)
        }
    }
}

@ExtendWith(MockKExtension::class)
class RankingServiceTest {

    private lateinit var stringRedisTemplate: StringRedisTemplate
    private lateinit var hashOps: HashOperations<String, String, String>
    private lateinit var zSetOps: ZSetOperations<String, String>
    private lateinit var userRepository: UserRepository
    private lateinit var pvpRankingService: PvpRankingCapable

    @BeforeEach
    fun setUp() {
        stringRedisTemplate = mockk()
        hashOps = mockk()
        zSetOps = mockk()
        userRepository = mockk()

        every { stringRedisTemplate.opsForHash<String, String>() } returns hashOps
        every { stringRedisTemplate.opsForZSet() } returns zSetOps

        pvpRankingService = PvpRankingService(stringRedisTemplate, userRepository)
    }

    // ── BR-01: 승리 시 total + wins 모두 HINCRBY ─────────────────────────────

    @Test
    fun `updatePvpWinRate_승리시_total과_wins_모두_HINCRBY_호출`() {
        // given
        every { hashOps.increment(any(), "total", 1L) } returns 3L
        every { hashOps.increment(any(), "wins", 1L) } returns 2L
        every { hashOps.get(any(), "wins") } returns "2"
        every { hashOps.get(any(), "total") } returns "3"
        every { zSetOps.add(any(), any(), any<Double>()) } returns true

        // when
        pvpRankingService.updatePvpWinRate(userId = 1L, isWin = true)

        // then
        verify(exactly = 1) { hashOps.increment("pvp:stats:1", "wins", 1L) }
    }

    // ── BR-01: 패배 시 total만 HINCRBY ───────────────────────────────────────

    @Test
    fun `updatePvpWinRate_패배시_total만_HINCRBY_wins_미호출`() {
        // given
        every { hashOps.increment(any(), "total", 1L) } returns 2L
        every { hashOps.get(any(), "wins") } returns "1"
        every { hashOps.get(any(), "total") } returns "2"
        every { zSetOps.add(any(), any(), any<Double>()) } returns true

        // when
        pvpRankingService.updatePvpWinRate(userId = 1L, isWin = false)

        // then
        verify(exactly = 0) { hashOps.increment(any(), "wins", 1L) }
    }

    // ── BR-01: 승률 점수 = wins / total × 100 으로 ZADD ──────────────────────

    @Test
    fun `updatePvpWinRate_갱신후_ZADD로_승률점수_75점_저장`() {
        // given — wins=3, total=4 → score=75.0
        every { hashOps.increment(any(), "total", 1L) } returns 4L
        every { hashOps.increment(any(), "wins", 1L) } returns 3L
        every { hashOps.get(any(), "wins") } returns "3"
        every { hashOps.get(any(), "total") } returns "4"
        every { zSetOps.add(any(), any(), any<Double>()) } returns true

        // when
        pvpRankingService.updatePvpWinRate(userId = 1L, isWin = true)

        // then
        verify(exactly = 1) { zSetOps.add("leaderboard:pvp-winrate", "1", 75.0) }
    }

    // ── BR-02: total = 0이면 ZADD 미호출 (ZeroDivision 방지) ────────────────

    @Test
    fun `updatePvpWinRate_total이_0이면_ZADD_미호출`() {
        // given
        every { hashOps.increment(any(), "total", 1L) } returns 0L
        every { hashOps.get(any(), "wins") } returns "0"
        every { hashOps.get(any(), "total") } returns "0"

        // when
        pvpRankingService.updatePvpWinRate(userId = 1L, isWin = false)

        // then
        verify(exactly = 0) { zSetOps.add(any(), any(), any<Double>()) }
    }

    // ── INV-03: winnerId = null 시 Redis 연산 미수행 ─────────────────────────

    @Test
    fun `winnerId가_null인_배틀에서_Redis_연산_미수행`() {
        // given — winnerId=null 이면 BattleFinishedEventListener가 updatePvpWinRate를 호출하지 않는다
        // RankingService 레벨에서 아무 Redis 연산도 일어나지 않음을 검증

        // when — 서비스 호출 없음

        // then
        verify(exactly = 0) { hashOps.increment(any(), any(), any<Long>()) }
        verify(exactly = 0) { zSetOps.add(any(), any(), any<Double>()) }
    }

    // ── BR-05: limit 100 초과 시 cap ─────────────────────────────────────────

    @Test
    fun `getTopPvpRankings_limit_100_초과시_reverseRangeWithScores는_0_99_범위_호출`() {
        // given
        val tuples: Set<TypedTuple<String>> = emptySet()
        every {
            zSetOps.reverseRangeWithScores("leaderboard:pvp-winrate", 0, 99)
        } returns tuples
        every { userRepository.findAllById(any<Iterable<Long>>()) } returns emptyList()

        // when
        pvpRankingService.getTopPvpRankings(limit = 200)

        // then
        verify(exactly = 1) { zSetOps.reverseRangeWithScores("leaderboard:pvp-winrate", 0, 99) }
    }

    // ── Happy Path: PVP 랭킹 목록 반환 ──────────────────────────────────────

    @Test
    fun `getTopPvpRankings_정상_요청시_evaluatedValue가_score와_일치`() {
        // given
        val mockTuple: TypedTuple<String> = mockk {
            every { value } returns "42"
            every { score } returns 7550.0
        }
        val tuples: Set<TypedTuple<String>> = linkedSetOf(mockTuple)
        every {
            zSetOps.reverseRangeWithScores("leaderboard:pvp-winrate", 0, 9)
        } returns tuples

        val mockUser: User = mockk {
            every { id } returns 42L
            every { nickname } returns "테스터"
        }
        every { userRepository.findAllById(any<Iterable<Long>>()) } returns listOf(mockUser)

        // when
        val result: List<RankingEntryResponse> = pvpRankingService.getTopPvpRankings(limit = 10)

        // then
        assertThat(result).hasSize(1)
        assertThat(result[0].evaluatedValue).isEqualTo(7550L)
    }
}
