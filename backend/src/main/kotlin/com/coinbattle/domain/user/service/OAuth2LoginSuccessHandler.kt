package com.coinbattle.domain.user.service

import com.coinbattle.domain.user.entity.CoinBattlePrincipal
import com.coinbattle.common.util.JwtProvider
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler
import org.springframework.stereotype.Component

@Component
class OAuth2LoginSuccessHandler(
    private val jwtProvider: JwtProvider,
    @Value("\${frontend.url}") private val frontendUrl: String
) : SimpleUrlAuthenticationSuccessHandler() {

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {
        val principal = authentication.principal as CoinBattlePrincipal
        val accessToken = jwtProvider.generateAccessToken(principal.user.id, principal.user.role.name)
        val refreshToken = jwtProvider.generateRefreshToken(principal.user.id)
        redirectStrategy.sendRedirect(
            request, response,
            "$frontendUrl/oauth2/callback?accessToken=$accessToken&refreshToken=$refreshToken"
        )
    }
}
