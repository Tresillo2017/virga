package com.tomasps.virga.wear.ui

import com.tomasps.virga.core.common.result.ApiError
import org.junit.Assert.assertEquals
import org.junit.Test

class ErrorKindTest {
    @Test
    fun networkIsOffline() = assertEquals(ErrorKind.Offline, ApiError.Network("x").toErrorKind())

    @Test
    fun rejectionAndRateLimitKeepTheirPhrases() {
        assertEquals(ErrorKind.TokenRejected, ApiError.Unauthorized().toErrorKind())
        assertEquals(ErrorKind.RateLimited, ApiError.RateLimited(1).toErrorKind())
    }

    @Test
    fun serverAndDecodeErrorsAreSomethingWentWrong() {
        assertEquals(ErrorKind.SomethingWrong, ApiError.Server(503).toErrorKind())
        assertEquals(ErrorKind.SomethingWrong, ApiError.Decode("bad").toErrorKind())
        assertEquals(ErrorKind.SomethingWrong, ApiError.Local("x").toErrorKind())
    }
}
