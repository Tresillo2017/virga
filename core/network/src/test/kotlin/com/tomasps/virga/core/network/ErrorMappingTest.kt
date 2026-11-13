package com.tomasps.virga.core.network

import com.tomasps.virga.core.network.demo.*
import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.CloudflareMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorMappingTest {
    @Test
    fun `maps statuses to typed errors`() {
        assertTrue(errorForStatus(401, emptyList(), null) is ApiError.Unauthorized)
        assertTrue(errorForStatus(403, emptyList(), null) is ApiError.Forbidden)
        assertTrue(errorForStatus(404, emptyList(), null) is ApiError.NotFound)
        assertTrue(errorForStatus(503, emptyList(), null) is ApiError.Server)
        assertTrue(errorForStatus(400, emptyList(), null) is ApiError.Rejected)
    }

    @Test
    fun `rate limited uses Retry-After when supplied and five minutes otherwise`() {
        val explicit = errorForStatus(429, emptyList(), 12_000L) as ApiError.RateLimited
        assertEquals(12_000L, explicit.retryAfterMillis)
        val fallback = errorForStatus(429, emptyList(), null) as ApiError.RateLimited
        assertEquals(DEFAULT_RETRY_AFTER_MILLIS, fallback.retryAfterMillis)
    }

    @Test
    fun `Retry-After header is read as whole seconds`() {
        assertEquals(30_000L, parseRetryAfterMillis("30"))
        assertNull(parseRetryAfterMillis(null))
        assertNull(parseRetryAfterMillis("   "))
    }

    @Test
    fun `Retry-After as an HTTP date becomes the time left until that date`() {
        val now = java.time.ZonedDateTime.parse("2026-11-11T12:00:00Z").toInstant().toEpochMilli()
        assertEquals(90_000L, parseRetryAfterMillis("Wed, 11 Nov 2026 12:01:30 GMT", nowEpochMillis = now))
        assertEquals(0L, parseRetryAfterMillis("Wed, 11 Nov 2026 11:00:00 GMT", nowEpochMillis = now))
    }

    @Test
    fun `unreadable Retry-After values are ignored`() {
        assertNull(parseRetryAfterMillis("soon"))
    }

    @Test
    fun `forbidden keeps the Cloudflare error codes`() {
        val error = errorForStatus(403, listOf(CloudflareMessage(code = 10000, message = "denied")), null)
        assertEquals(listOf(10000), error.codes)
    }
}
