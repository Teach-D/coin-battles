package com.coinbattle.common.config

import com.coinbattle.common.exception.CoinBattleException
import com.coinbattle.common.util.JwtProvider
import com.coinbattle.domain.user.entity.CoinBattlePrincipal
import com.coinbattle.domain.user.repository.UserRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

class JwtAuthenticationFilter(
    private val jwtProvider: JwtProvider,
    private val userRepository: UserRepository
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val token = extractBearerToken(request)
        if (token != null) {
            try {
                if (jwtProvider.validate(token)) {
                    val userId = jwtProvider.getUserId(token)
                    userRepository.findById(userId).ifPresent { user ->
                        val principal = CoinBattlePrincipal(user)
                        val auth = UsernamePasswordAuthenticationToken(
                            principal, null, principal.authorities
                        )
                        SecurityContextHolder.getContext().authentication = auth
                    }
                }
            } catch (e: CoinBattleException) {
            }
        }
        filterChain.doFilter(request, response)
    }

    private fun extractBearerToken(request: HttpServletRequest): String? {
        val header = request.getHeader("Authorization") ?: return null
        if (!header.startsWith("Bearer ")) return null
        return header.substring(7)
    }
}
