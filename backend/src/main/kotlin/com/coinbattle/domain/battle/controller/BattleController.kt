package com.coinbattle.domain.battle.controller

import com.coinbattle.common.dto.ApiResponse
import com.coinbattle.domain.battle.dto.request.CreateBattleRequest
import com.coinbattle.domain.battle.dto.request.MatchBattleRequest
import com.coinbattle.domain.battle.dto.response.BattleListResponse
import com.coinbattle.domain.battle.dto.response.BattleResponse
import com.coinbattle.domain.battle.dto.response.BattleResultResponse
import com.coinbattle.domain.battle.dto.response.InviteCodeResponse
import com.coinbattle.domain.battle.dto.response.JoinBattleResponse
import com.coinbattle.domain.battle.dto.response.JoinByInviteResponse
import com.coinbattle.domain.battle.dto.response.MatchQueueResponse
import com.coinbattle.domain.battle.enum.BattleStatus
import com.coinbattle.domain.battle.service.BattleEndService
import com.coinbattle.domain.battle.service.BattleMatchingService
import com.coinbattle.domain.battle.service.BattleService
import com.coinbattle.domain.battle.service.InviteService
import com.coinbattle.domain.user.entity.CoinBattlePrincipal
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/battles")
class BattleController(
    private val battleService: BattleService,
    private val battleMatchingService: BattleMatchingService,
    private val battleEndService: BattleEndService,
    private val inviteService: InviteService
) {
    @PostMapping
    fun createBattle(
        @AuthenticationPrincipal principal: CoinBattlePrincipal,
        @Valid @RequestBody request: CreateBattleRequest
    ): ResponseEntity<ApiResponse<BattleResponse>> {
        val result = battleService.createBattle(principal.user.id, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(result))
    }

    @PostMapping("/{battleId}/join")
    fun joinBattle(
        @AuthenticationPrincipal principal: CoinBattlePrincipal,
        @PathVariable battleId: UUID
    ): ResponseEntity<ApiResponse<JoinBattleResponse>> {
        val result = battleService.joinBattle(principal.user.id, battleId)
        return ResponseEntity.ok(ApiResponse.ok(result))
    }

    @GetMapping("/{battleId}")
    fun getBattle(
        @PathVariable battleId: UUID
    ): ResponseEntity<ApiResponse<BattleResponse>> {
        val result = battleService.getBattle(battleId)
        return ResponseEntity.ok(ApiResponse.ok(result))
    }

    @GetMapping
    fun getBattleList(
        @RequestParam(defaultValue = "WAITING") status: BattleStatus,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<ApiResponse<BattleListResponse>> {
        val result = battleService.getBattleList(status, page, size)
        return ResponseEntity.ok(ApiResponse.ok(result))
    }

    @PostMapping("/match")
    fun joinMatchQueue(
        @AuthenticationPrincipal principal: CoinBattlePrincipal,
        @Valid @RequestBody request: MatchBattleRequest
    ): ResponseEntity<ApiResponse<MatchQueueResponse>> {
        val result = battleMatchingService.joinMatchQueue(principal.user.id, request)
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(result))
    }

    @DeleteMapping("/match")
    fun leaveMatchQueue(
        @AuthenticationPrincipal principal: CoinBattlePrincipal
    ): ResponseEntity<Void> {
        battleMatchingService.leaveMatchQueue(principal.user.id)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/{battleId}/result")
    fun getBattleResult(
        @AuthenticationPrincipal principal: CoinBattlePrincipal,
        @PathVariable battleId: UUID
    ): ResponseEntity<ApiResponse<BattleResultResponse>> {
        val result = battleEndService.getBattleResult(battleId, principal.user.id)
        return ResponseEntity.ok(ApiResponse.ok(result))
    }

    @PostMapping("/{battleId}/invite")
    fun generateInviteCode(
        @AuthenticationPrincipal principal: CoinBattlePrincipal,
        @PathVariable battleId: UUID
    ): ResponseEntity<ApiResponse<InviteCodeResponse>> {
        val result = inviteService.generateInviteCode(battleId, principal.user.id)
        return ResponseEntity.ok(ApiResponse.ok(result))
    }

    @PostMapping("/join/{inviteCode}")
    fun joinByInviteCode(
        @AuthenticationPrincipal principal: CoinBattlePrincipal,
        @PathVariable inviteCode: String
    ): ResponseEntity<ApiResponse<JoinByInviteResponse>> {
        val result = inviteService.joinByInvite(inviteCode, principal.user.id)
        return ResponseEntity.ok(ApiResponse.ok(result))
    }
}
