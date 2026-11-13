package com.tomasps.virga.core.network

import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.CloudflareMessage
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.math.max

const val DEFAULT_RETRY_AFTER_MILLIS = 300_000L

private const val MILLIS_PER_SECOND = 1_000L
private const val STATUS_UNAUTHORIZED = 401
private const val STATUS_FORBIDDEN = 403
private const val STATUS_NOT_FOUND = 404
private const val STATUS_TOO_MANY_REQUESTS = 429
private const val STATUS_SERVER_MIN = 500
private const val STATUS_SERVER_MAX = 599

/** Maps an HTTP failure status to a typed [ApiError]. */
fun errorForStatus(status: Int, messages: List<CloudflareMessage>, retryAfterMillis: Long?): ApiError = when (status) {
    STATUS_UNAUTHORIZED -> ApiError.Unauthorized(messages)
    STATUS_FORBIDDEN -> ApiError.Forbidden(messages)
    STATUS_NOT_FOUND -> ApiError.NotFound(messages)
    STATUS_TOO_MANY_REQUESTS -> ApiError.RateLimited(retryAfterMillis ?: DEFAULT_RETRY_AFTER_MILLIS, messages)
    in STATUS_SERVER_MIN..STATUS_SERVER_MAX -> ApiError.Server(status, messages)
    else -> ApiError.Rejected(status, messages)
}

/**
 * Retry-After in either form: delta-seconds, or an HTTP-date (RFC 1123) which becomes the time left until
 * that date. Returns null when the header is absent or unreadable, so the caller applies its default.
 */
fun parseRetryAfterMillis(headerValue: String?, nowEpochMillis: Long = System.currentTimeMillis()): Long? {
    val value = headerValue?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    value.toLongOrNull()?.let { seconds -> return seconds * MILLIS_PER_SECOND }
    val retryAt = try {
        ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
    } catch (error: DateTimeParseException) {
        return null
    }
    return max(0L, retryAt - nowEpochMillis)
}

fun isSuccessStatus(status: Int): Boolean = status in 200..299
