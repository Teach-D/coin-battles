package com.coinbattle.domain.user

import com.coinbattle.common.exception.CoinBattleException
import com.coinbattle.common.exception.ErrorCode
import com.coinbattle.common.util.JwtProvider
import com.coinbattle.domain.battle.repository.BattleSessionRepository
import com.coinbattle.domain.user.entity.AuthProvider
import com.coinbattle.domain.user.entity.User
import com.coinbattle.domain.user.repository.UserRepository
import com.coinbattle.domain.user.service.UserService
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.Optional

@ExtendWith(MockKExtension::class)
class UserServiceTest {

    @MockK
    lateinit var userRepository: UserRepository

    @MockK
    lateinit var battleSessionRepository: BattleSessionRepository

    @MockK
    lateinit var jwtProvider: JwtProvider

    lateinit var userService: UserService

    @BeforeEach
    fun setUp() {
        userService = UserService(userRepository, battleSessionRepository, jwtProvider)
    }

    @Test
    fun `updateNickname_자신의_닉네임과_동일하면_성공`() {
        val user = createUser(1L, "sameNick")
        every { userRepository.findById(1L) } returns Optional.of(user)

        val result = userService.updateNickname(1L, "sameNick")

        assertThat(result.nickname).isEqualTo("sameNick")
    }

    @Test
    fun `updateNickname_중복_닉네임이면_예외`() {
        val user = createUser(1L, "oldNick")
        every { userRepository.findById(1L) } returns Optional.of(user)
        every { userRepository.existsByNickname("dupNick") } returns true

        assertThatThrownBy { userService.updateNickname(1L, "dupNick") }
            .isInstanceOf(CoinBattleException::class.java)
            .hasMessageContaining(ErrorCode.DUPLICATE_NICKNAME.message)
    }

    @Test
    fun `updateNickname_정상_변경`() {
        val user = createUser(1L, "oldNick")
        every { userRepository.findById(1L) } returns Optional.of(user)
        every { userRepository.existsByNickname("newNick") } returns false

        val result = userService.updateNickname(1L, "newNick")

        assertThat(result.nickname).isEqualTo("newNick")
        assertThat(result.userId).isEqualTo(1L)
    }

    @Test
    fun `getUserStats_배틀_없을때_winRate_null`() {
        every { battleSessionRepository.countWins(1L) } returns 0L
        every { battleSessionRepository.countLosses(1L) } returns 0L
        every { battleSessionRepository.countDraws(1L) } returns 0L
        every { battleSessionRepository.findBestReturnRate(1L) } returns null

        val result = userService.getUserStats(1L)

        assertThat(result.wins).isEqualTo(0)
        assertThat(result.totalGames).isEqualTo(0)
        assertThat(result.winRate).isNull()
        assertThat(result.bestReturnRate).isNull()
    }

    @Test
    fun `getUserStats_정상_승률_계산`() {
        every { battleSessionRepository.countWins(1L) } returns 3L
        every { battleSessionRepository.countLosses(1L) } returns 1L
        every { battleSessionRepository.countDraws(1L) } returns 0L
        every { battleSessionRepository.findBestReturnRate(1L) } returns 42.5

        val result = userService.getUserStats(1L)

        assertThat(result.wins).isEqualTo(3)
        assertThat(result.losses).isEqualTo(1)
        assertThat(result.totalGames).isEqualTo(4)
        assertThat(result.winRate).isEqualTo(75.0)
        assertThat(result.bestReturnRate).isEqualTo(42.5)
    }

    private fun createUser(id: Long, nickname: String): User {
        val user = User(
            email = "user$id@test.com",
            nickname = nickname,
            provider = AuthProvider.KAKAO,
            providerId = "provider_$id"
        )
        val field = User::class.java.getDeclaredField("id")
        field.isAccessible = true
        field.set(user, id)
        return user
    }
}
