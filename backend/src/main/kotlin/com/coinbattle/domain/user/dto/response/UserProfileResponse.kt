package com.coinbattle.domain.user.dto.response

data class UserProfileResponse(
    val userId: Long,
    val nickname: String,
    val profileImageUrl: String?,
    val email: String
)
