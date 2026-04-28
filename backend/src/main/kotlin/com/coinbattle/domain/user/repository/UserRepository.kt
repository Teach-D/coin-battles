package com.coinbattle.domain.user.repository

import com.coinbattle.domain.user.entity.AuthProvider
import com.coinbattle.domain.user.entity.User
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long> {
    fun findByProviderAndProviderId(provider: AuthProvider, providerId: String): User?
    fun existsByNickname(nickname: String): Boolean
}
