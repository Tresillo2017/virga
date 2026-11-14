package com.tomasps.virga.wear.ui

import com.tomasps.virga.core.common.result.ApiError

/** The failures the watch tells the wearer about. Each has one short fixed phrase in strings.xml. */
enum class ErrorKind { Offline, TokenRejected, RateLimited, SomethingWrong }

/** Only a network failure reads as "Offline"; server, decode and local failures are "Something went wrong". */
fun ApiError.toErrorKind(): ErrorKind = when (this) {
    is ApiError.Unauthorized, is ApiError.Forbidden -> ErrorKind.TokenRejected
    is ApiError.RateLimited -> ErrorKind.RateLimited
    is ApiError.Network -> ErrorKind.Offline
    else -> ErrorKind.SomethingWrong
}
