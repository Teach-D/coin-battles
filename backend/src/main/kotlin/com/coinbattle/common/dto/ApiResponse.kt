package com.coinbattle.common.dto

data class ApiResponse<T>(
    val success: Boolean,
    val data: T?,
    val message: String?
) {
    companion object {
        fun <T> ok(data: T) = ApiResponse(true, data, null)
        fun error(message: String) = ApiResponse<Nothing>(false, null, message)
    }
}
