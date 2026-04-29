package com.coinbattle.common.exception

enum class ErrorCode(val status: Int, val message: String) {
    USER_NOT_FOUND(404, "사용자를 찾을 수 없습니다"),
    INVALID_TOKEN(401, "유효하지 않은 토큰입니다"),
    EXPIRED_TOKEN(401, "만료된 토큰입니다"),
    DUPLICATE_NICKNAME(409, "이미 사용 중인 닉네임입니다"),
    TICKER_NOT_FOUND(404, "시세 정보를 찾을 수 없습니다"),
    MARKET_DATA_UNAVAILABLE(503, "시세 데이터를 사용할 수 없습니다"),
    PORTFOLIO_NOT_FOUND(404, "포트폴리오를 찾을 수 없습니다"),
    INVALID_ORDER_AMOUNT(400, "주문 금액은 최소 1,000원 이상이어야 합니다"),
    INVALID_LEVERAGE(400, "레버리지는 1~10 사이여야 합니다"),
    LIMIT_PRICE_REQUIRED(400, "지정가 주문에는 limitPrice가 필요합니다"),
    INVALID_CLOSE_RATIO(400, "청산 비율은 0 초과 1.0 이하여야 합니다"),
    DUPLICATE_ORDER(409, "중복된 주문입니다"),
    INSUFFICIENT_BALANCE(409, "잔고가 부족합니다"),
    ORDER_LOCK_TIMEOUT(423, "주문 처리 중입니다. 잠시 후 다시 시도해주세요"),
    POSITION_NOT_FOUND(404, "포지션을 찾을 수 없습니다"),
    POSITION_NOT_OWNED(403, "본인의 포지션이 아닙니다"),
    POSITION_ALREADY_CLOSED(409, "이미 청산된 포지션입니다"),
    INVALID_CANDLE_UNIT(400, "유효하지 않은 분봉 단위입니다. 허용값: 1, 3, 5, 10, 15, 30, 60, 240"),
    CANDLE_DATA_UNAVAILABLE(503, "캔들 데이터를 가져올 수 없습니다"),
}
