package com.coinbattle.domain.user.controller

import com.coinbattle.common.dto.ApiResponse
import com.coinbattle.domain.user.dto.request.RefreshTokenRequest
import com.coinbattle.domain.user.dto.response.AccessTokenResponse
import com.coinbattle.domain.user.service.UserService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(private val userService: UserService) {

    @PostMapping("/refresh")
    fun refresh(
        @Valid @RequestBody request: RefreshTokenRequest
    ): ResponseEntity<ApiResponse<AccessTokenResponse>> =
        ResponseEntity.ok(ApiResponse.ok(userService.refreshAccessToken(request.refreshToken)))
}
