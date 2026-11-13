package com.tomasps.virga.core.common.result

/** Outcome of a Cloudflare API call. [Success.pageInfo] is set when the response carried result_info. */
sealed interface ApiResult<out T> {
    data class Success<out T>(val value: T, val pageInfo: PageInfo? = null) : ApiResult<T>
    data class Failure(val error: ApiError) : ApiResult<Nothing>
}

fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(value), pageInfo)
    is ApiResult.Failure -> this
}

fun <T> ApiResult<T>.getOrNull(): T? = (this as? ApiResult.Success)?.value

fun <T> ApiResult<T>.errorOrNull(): ApiError? = (this as? ApiResult.Failure)?.error
