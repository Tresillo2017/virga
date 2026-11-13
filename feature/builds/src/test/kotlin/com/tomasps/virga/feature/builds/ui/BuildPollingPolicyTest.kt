package com.tomasps.virga.feature.builds.ui

import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.feature.builds.watch.WatchPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildPollingPolicyTest {

    @Test
    fun `not found, unauthorized and forbidden stop polling`() {
        assertTrue(classifyBuildFailure(ApiError.NotFound()) is BuildFetch.Stop)
        assertTrue(classifyBuildFailure(ApiError.Unauthorized()) is BuildFetch.Stop)
        assertTrue(classifyBuildFailure(ApiError.Forbidden()) is BuildFetch.Stop)
    }

    @Test
    fun `rate limits, network and server errors keep polling`() {
        assertTrue(classifyBuildFailure(ApiError.RateLimited(1_000L)) is BuildFetch.Retry)
        assertTrue(classifyBuildFailure(ApiError.Network("offline")) is BuildFetch.Retry)
        assertTrue(classifyBuildFailure(ApiError.Server(500)) is BuildFetch.Retry)
    }

    @Test
    fun `a rate limit waits for the server's Retry-After, bounded`() {
        assertEquals(20_000L, nextRetryDelay(3_000L, ApiError.RateLimited(20_000L), pollMillis = 3_000L))
        assertEquals(
            WatchPolicy.MAX_RETRY_AFTER_MS,
            nextRetryDelay(3_000L, ApiError.RateLimited(Long.MAX_VALUE), pollMillis = 3_000L),
        )
    }

    @Test
    fun `other failures double the wait up to the maximum backoff`() {
        assertEquals(6_000L, nextRetryDelay(3_000L, ApiError.Server(500), pollMillis = 3_000L))
        assertEquals(WatchPolicy.MAX_BACKOFF_MS, nextRetryDelay(40_000L, ApiError.Server(500), pollMillis = 3_000L))
    }
}
