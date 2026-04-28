package com.coinbattle.domain.user.entity

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.oauth2.core.user.OAuth2User

class CoinBattlePrincipal(
    val user: User,
    private val attributes: Map<String, Any> = emptyMap()
) : OAuth2User, UserDetails {
    override fun getName() = user.id.toString()
    override fun getAttributes() = attributes
    override fun getAuthorities(): Collection<GrantedAuthority> =
        listOf(SimpleGrantedAuthority(user.role.name))
    override fun getPassword() = null
    override fun getUsername() = user.email
}
