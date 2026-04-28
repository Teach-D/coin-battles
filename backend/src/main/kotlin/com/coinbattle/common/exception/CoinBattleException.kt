package com.coinbattle.common.exception

class CoinBattleException(val errorCode: ErrorCode) : RuntimeException(errorCode.message)
