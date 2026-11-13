package com.tomasps.virga.feature.builds.watch

import com.tomasps.virga.core.network.RateBudget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchPolicyTest {

    private fun budget(remaining: Int, blockedMillis: Long = 0L) = RateBudget(
        restRemaining = remaining,
        restCapacity = 150,
        graphQlRemaining = 40,
        graphQlCapacity = 40,
        blockedForMillis = blockedMillis,
    )

    @Test
    fun `polls every five seconds while the budget is healthy`() {
        assertEquals(WatchPolicy.BASE_INTERVAL_MS, WatchPolicy.intervalMillis(budget(remaining = 150)))
    }

    @Test
    fun `slows to fifteen seconds when the REST budget is low or blocked`() {
        assertEquals(WatchPolicy.LOW_BUDGET_INTERVAL_MS, WatchPolicy.intervalMillis(budget(remaining = 30)))
        assertEquals(WatchPolicy.LOW_BUDGET_INTERVAL_MS, WatchPolicy.intervalMillis(budget(remaining = 150, blockedMillis = 1_000)))
    }

    @Test
    fun `a 429 uses its Retry-After, bounded to the allowed range`() {
        assertEquals(20_000L, WatchPolicy.backoffAfterError(currentBackoffMs = 0L, retryAfterMs = 20_000L))
        assertEquals(WatchPolicy.MIN_BACKOFF_MS, WatchPolicy.backoffAfterError(0L, retryAfterMs = 10L))
        assertEquals(WatchPolicy.MAX_RETRY_AFTER_MS, WatchPolicy.backoffAfterError(0L, retryAfterMs = 99_000_000L))
    }

    @Test
    fun `other failures double the wait up to the cap`() {
        assertEquals(WatchPolicy.MIN_BACKOFF_MS, WatchPolicy.backoffAfterError(0L, null))
        assertEquals(10_000L, WatchPolicy.backoffAfterError(WatchPolicy.MIN_BACKOFF_MS, null))
        assertEquals(WatchPolicy.MAX_BACKOFF_MS, WatchPolicy.backoffAfterError(WatchPolicy.MAX_BACKOFF_MS, null))
    }

    @Test
    fun `a watch expires exactly at the two hour cap`() {
        assertFalse(WatchPolicy.isExpired(startedAtMillis = 0L, nowMillis = WatchPolicy.MAX_WATCH_MS - 1))
        assertTrue(WatchPolicy.isExpired(startedAtMillis = 0L, nowMillis = WatchPolicy.MAX_WATCH_MS))
    }

    @Test
    fun `the loop wakes no sooner than a second and no later than five seconds`() {
        assertEquals(WatchPolicy.MIN_LOOP_DELAY_MS, WatchPolicy.loopDelayMillis(nextDueInMillis = 0L))
        assertEquals(3_000L, WatchPolicy.loopDelayMillis(nextDueInMillis = 3_000L))
        assertEquals(WatchPolicy.MAX_LOOP_DELAY_MS, WatchPolicy.loopDelayMillis(nextDueInMillis = null))
        assertEquals(WatchPolicy.MAX_LOOP_DELAY_MS, WatchPolicy.loopDelayMillis(nextDueInMillis = 60_000L))
    }
}
