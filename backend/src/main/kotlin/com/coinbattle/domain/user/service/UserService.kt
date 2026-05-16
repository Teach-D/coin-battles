package com.coinbattle.domain.user.service

import com.coinbattle.common.exception.CoinBattleException
import com.coinbattle.common.exception.ErrorCode
import com.coinbattle.common.util.JwtProvider
import com.coinbattle.domain.battle.repository.BattleSessionRepository
import com.coinbattle.domain.user.dto.response.AccessTokenResponse
import com.coinbattle.domain.user.dto.response.TokenResponse
import com.coinbattle.domain.user.dto.response.UserProfileResponse
import com.coinbattle.domain.user.dto.response.UserStatsResponse
import com.coinbattle.domain.user.entity.AuthProvider
import com.coinbattle.domain.user.entity.User
import com.coinbattle.domain.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class UserService(
    private val userRepository: UserRepository,
    private val battleSessionRepository: BattleSessionRepository,
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

    @Transactional(readOnly = true)
    fun getProfile(userId: Long): UserProfileResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { CoinBattleException(ErrorCode.USER_NOT_FOUND) }
        return UserProfileResponse(user.id, user.nickname, user.profileImageUrl, user.email)
    }

    fun updateNickname(userId: Long, nickname: String): UserProfileResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { CoinBattleException(ErrorCode.USER_NOT_FOUND) }
        if (user.nickname != nickname && userRepository.existsByNickname(nickname)) {
            throw CoinBattleException(ErrorCode.DUPLICATE_NICKNAME)
        }
        user.nickname = nickname
        return UserProfileResponse(user.id, user.nickname, user.profileImageUrl, user.email)
    }

    @Transactional(readOnly = true)
    fun getUserStats(userId: Long): UserStatsResponse {
        val wins = battleSessionRepository.countWins(userId).toInt()
        val losses = battleSessionRepository.countLosses(userId).toInt()
        val draws = battleSessionRepository.countDraws(userId).toInt()
        val total = wins + losses + draws
        return UserStatsResponse(
            wins = wins,
            losses = losses,
            draws = draws,
            totalGames = total,
            winRate = if (total > 0) wins.toDouble() / total * 100 else null,
            bestReturnRate = battleSessionRepository.findBestReturnRate(userId)
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
