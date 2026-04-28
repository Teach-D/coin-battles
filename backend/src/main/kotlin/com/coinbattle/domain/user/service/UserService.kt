package com.coinbattle.domain.user.service

import com.coinbattle.common.exception.CoinBattleException
import com.coinbattle.common.exception.ErrorCode
import com.coinbattle.common.util.JwtProvider
import com.coinbattle.domain.user.dto.response.AccessTokenResponse
import com.coinbattle.domain.user.dto.response.TokenResponse
import com.coinbattle.domain.user.entity.AuthProvider
import com.coinbattle.domain.user.entity.User
import com.coinbattle.domain.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class UserService(
    private val userRepository: UserRepository,
    private val jwtProvider: JwtProvider
) {
    fun findOrCreateSocialUser(
        email: String,
        nickname: String,
        profileImageUrl: String?,
        provider: AuthProvider,
        providerId: String
    ): User {
        return userRepository.findByProviderAndProviderId(provider, providerId)
            ?: userRepository.save(
                User(
                    email = email,
                    nickname = generateUniqueNickname(nickname),
                    profileImageUrl = profileImageUrl,
                    provider = provider,
                    providerId = providerId
                )
            )
    }

    fun issueTokens(user: User): TokenResponse = TokenResponse(
        accessToken = jwtProvider.generateAccessToken(user.id, user.role.name),
        refreshToken = jwtProvider.generateRefreshToken(user.id)
    )

    fun refreshAccessToken(refreshToken: String): AccessTokenResponse {
        jwtProvider.validate(refreshToken)
        val userId = jwtProvider.getUserId(refreshToken)
        val user = userRepository.findById(userId)
            .orElseThrow { CoinBattleException(ErrorCode.USER_NOT_FOUND) }
        return AccessTokenResponse(
            accessToken = jwtProvider.generateAccessToken(user.id, user.role.name)
        )
    }

    private fun generateUniqueNickname(base: String): String {
        var nickname = base
        var suffix = 1
        while (userRepository.existsByNickname(nickname)) {
            nickname = "${base}_${suffix++}"
        }
        return nickname
    }
}
