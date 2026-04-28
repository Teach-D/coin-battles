package com.coinbattle.domain.user.dto.response

data class TokenResponse(
    val accessToken: String,
    val refreshToken: String
)
