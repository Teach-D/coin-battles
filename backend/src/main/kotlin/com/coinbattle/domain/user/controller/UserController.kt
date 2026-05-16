package com.coinbattle.domain.user.controller

import com.coinbattle.common.dto.ApiResponse
import com.coinbattle.domain.user.dto.request.UpdateProfileRequest
import com.coinbattle.domain.user.dto.response.UserProfileResponse
import com.coinbattle.domain.user.dto.response.UserStatsResponse
import com.coinbattle.domain.user.entity.CoinBattlePrincipal
import com.coinbattle.domain.user.service.UserService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/users")
class UserController(private val userService: UserService) {

    @GetMapping("/me")
    fun getProfile(
        @AuthenticationPrincipal principal: CoinBattlePrincipal
    ): ResponseEntity<ApiResponse<UserProfileResponse>> =
        ResponseEntity.ok(ApiResponse.ok(userService.getProfile(principal.user.id)))

    @PatchMapping("/me")
    fun updateProfile(
        @AuthenticationPrincipal principal: CoinBattlePrincipal,
        @Valid @RequestBody request: UpdateProfileRequest
    ): ResponseEntity<ApiResponse<UserProfileResponse>> =
        ResponseEntity.ok(ApiResponse.ok(userService.updateNickname(principal.user.id, request.nickname)))

    @GetMapping("/me/stats")
    fun getStats(
        @AuthenticationPrincipal principal: CoinBattlePrincipal
    ): ResponseEntity<ApiResponse<UserStatsResponse>> =
        ResponseEntity.ok(ApiResponse.ok(userService.getUserStats(principal.user.id)))
}
