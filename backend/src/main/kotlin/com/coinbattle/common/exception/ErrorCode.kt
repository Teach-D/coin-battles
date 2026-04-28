package com.coinbattle.common.exception

enum class ErrorCode(val status: Int, val message: String) {
    USER_NOT_FOUND(404, "사용자를 찾을 수 없습니다"),
    INVALID_TOKEN(401, "유효하지 않은 토큰입니다"),
    EXPIRED_TOKEN(401, "만료된 토큰입니다"),
    DUPLICATE_NICKNAME(409, "이미 사용 중인 닉네임입니다"),
}
