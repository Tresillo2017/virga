package com.tomasps.virga.core.common.result

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiErrorTest {

    @Test
    fun `codes lists only the messages that carry a Cloudflare code`() {
        val error = ApiError.Forbidden(
            listOf(
                CloudflareMessage(code = 10000, message = "Authentication error"),
                CloudflareMessage(code = null, message = "No code here"),
                CloudflareMessage(code = 9109, message = "Insufficient permissions"),
            ),
        )

        assertEquals(listOf(10000, 9109), error.codes)
    }

    @Test
    fun `errors that no Cloudflare response describes carry no messages and no codes`() {
        val local: List<ApiError> = listOf(ApiError.Network("timeout"), ApiError.Decode("bad json"), ApiError.Local("refused"))

        assertTrue(local.all { it.messages.isEmpty() && it.codes.isEmpty() })
    }

    @Test
    fun `a rate limit keeps its retry delay and its messages`() {
        val limited = ApiError.RateLimited(retryAfterMillis = 4_000, messages = listOf(CloudflareMessage(code = 971, message = "slow down")))

        assertEquals(4_000L, limited.retryAfterMillis)
        assertEquals(listOf(971), limited.codes)
    }
}
