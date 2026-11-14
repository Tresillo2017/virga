package com.tomasps.virga.wear.ui

import com.tomasps.virga.core.common.result.ApiError

/** The only three failures the watch tells the wearer about. Each has one short fixed phrase in strings.xml. */
enum class ErrorKind { Offline, TokenRejected, RateLimited }

/** Anything that is not a rejection or a rate limit reads as "Offline": the wearer can only retry. */
fun ApiError.toErrorKind(): ErrorKind = when (this) {
    is ApiError.Unauthorized, is ApiError.Forbidden -> ErrorKind.TokenRejected
    is ApiError.RateLimited -> ErrorKind.RateLimited
    else -> ErrorKind.Offline
}
