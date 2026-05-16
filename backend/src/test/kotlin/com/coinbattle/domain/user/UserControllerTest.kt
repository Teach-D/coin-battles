package com.coinbattle.domain.user

import com.coinbattle.common.exception.CoinBattleException
import com.coinbattle.common.exception.ErrorCode
import com.coinbattle.domain.user.controller.UserController
import com.coinbattle.domain.user.dto.request.UpdateProfileRequest
import com.coinbattle.domain.user.dto.response.UserProfileResponse
import com.coinbattle.domain.user.dto.response.UserStatsResponse
import com.coinbattle.domain.user.entity.AuthProvider
import com.coinbattle.domain.user.entity.CoinBattlePrincipal
import com.coinbattle.domain.user.entity.User
import com.coinbattle.domain.user.service.UserService
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class UserControllerTest {

    @MockK
    lateinit var userService: UserService

    lateinit var userController: UserController

    @BeforeEach
    fun setUp() {
        userController = UserController(userService)
    }

    @Test
    fun `PATCH_닉네임_중복_예외_전파`() {
        val principal = createPrincipal(1L)
        every { userService.updateNickname(1L, "dup") } throws
            CoinBattleException(ErrorCode.DUPLICATE_NICKNAME)

        assertThatThrownBy {
            userController.updateProfile(principal, UpdateProfileRequest("dup"))
        }.isInstanceOf(CoinBattleException::class.java)
            .hasMessageContaining(ErrorCode.DUPLICATE_NICKNAME.message)
    }

    @Test
    fun `PATCH_닉네임_성공_200_반환`() {
        val principal = createPrincipal(1L)
        val response = UserProfileResponse(1L, "newNick", null, "test@test.com")
        every { userService.updateNickname(1L, "newNick") } returns response

        val result = userController.updateProfile(principal, UpdateProfileRequest("newNick"))

        assertThat(result.statusCode.value()).isEqualTo(200)
        assertThat(result.body?.data?.nickname).isEqualTo("newNick")
    }

    @Test
    fun `GET_stats_정상_반환`() {
        val principal = createPrincipal(1L)
        val stats = UserStatsResponse(3, 1, 0, 4, 75.0, 42.5)
        every { userService.getUserStats(1L) } returns stats

        val result = userController.getStats(principal)

        assertThat(result.statusCode.value()).isEqualTo(200)
        assertThat(result.body?.data?.wins).isEqualTo(3)
        assertThat(result.body?.data?.winRate).isEqualTo(75.0)
    }

    private fun createPrincipal(userId: Long): CoinBattlePrincipal {
        val user = User(
            email = "user$userId@test.com",
            nickname = "user$userId",
            provider = AuthProvider.KAKAO,
            providerId = "provider_$userId"
        )
        val field = User::class.java.getDeclaredField("id")
        field.isAccessible = true
        field.set(user, userId)
        return CoinBattlePrincipal(user)
    }
}
