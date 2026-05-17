package com.coinbattle.common.config

import com.coinbattle.common.util.JwtProvider
import com.coinbattle.domain.user.repository.UserRepository
import com.coinbattle.domain.user.service.OAuth2LoginSuccessHandler
import com.coinbattle.domain.user.service.OAuth2UserService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtProvider: JwtProvider,
    private val userRepository: UserRepository,
    private val oAuth2UserService: OAuth2UserService,
    private val oAuth2LoginSuccessHandler: OAuth2LoginSuccessHandler
) {
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration()
        config.allowedOrigins = listOf("http://localhost:5173")
        config.allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        config.allowedHeaders = listOf("*")
        config.allowCredentials = true
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", config)
        return source
    }

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { it.configurationSource(corsConfigurationSource()) }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers(
                    "/api/auth/**",
                    "/api/market/**",
                    "/api/ranking/season",
                    "/api/ranking/daily",
                    "/api/rankings",
                    "/oauth2/**",
                    "/login/**",
                    "/ws-connect/**",
                    "/actuator/health"
                ).permitAll()
                it.anyRequest().authenticated()
            }
            .oauth2Login {
                it.userInfoEndpoint { endpoint -> endpoint.userService(oAuth2UserService) }
                it.successHandler(oAuth2LoginSuccessHandler)
            }
            .addFilterBefore(
                JwtAuthenticationFilter(jwtProvider, userRepository),
                UsernamePasswordAuthenticationFilter::class.java
            )
        return http.build()
    }
}
