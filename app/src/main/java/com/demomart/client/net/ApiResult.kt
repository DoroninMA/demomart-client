package com.demomart.client.net

sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>
    data class Err(val code: String, val message: String = errorText(code)) : ApiResult<Nothing>
}
