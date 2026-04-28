package com.coinbattle.domain.user.dto.request

import jakarta.validation.constraints.NotBlank

data class RefreshTokenRequest(
    @field:NotBlank val refreshToken: String
)
