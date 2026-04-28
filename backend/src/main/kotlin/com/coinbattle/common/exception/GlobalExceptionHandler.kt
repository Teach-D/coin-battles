package com.coinbattle.common.exception

import com.coinbattle.common.dto.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(CoinBattleException::class)
    fun handleCoinBattleException(e: CoinBattleException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity
            .status(e.errorCode.status)
            .body(ApiResponse.error(e.errorCode.message))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(e: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Nothing>> {
        val message = e.bindingResult.fieldErrors
            .firstOrNull()?.defaultMessage ?: "유효하지 않은 요청입니다"
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(message))
    }

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error("서버 내부 오류가 발생했습니다"))
}
