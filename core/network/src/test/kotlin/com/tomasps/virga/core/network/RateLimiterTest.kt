package com.tomasps.virga.core.network

import com.tomasps.virga.core.network.demo.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RateLimiterTest {
    private val nanosPerMilli = 1_000_000L
    private val windowMillis = 300_000L
    private val REST_WINDOW_MAX = 900
    private val GRAPHQL_WINDOW_MAX = 240

    private fun maxInAnyWindow(grants: List<Long>): Int {
        var best = 0
        for (start in grants) {
            val inWindow = grants.count { it >= start && it < start + windowMillis }
            if (inWindow > best) best = inWindow
        }
        return best
    }

    @Test
    fun `rest traffic never exceeds the budget in any five minute window`() = runTest {
        val limiter = RateLimiter(
            nowNanos = { testScheduler.currentTime * nanosPerMilli },
            delayMillis = { delay(it) },
        )
        val grants = mutableListOf<Long>()
        repeat(2_000) {
            launch {
                limiter.acquire(RateLane.REST)
                grants += testScheduler.currentTime
            }
        }
        advanceUntilIdle()
        assertEquals(2_000, grants.size)
        // Burst 150 plus a refill of 750 per window is the documented ceiling of 900. The window is half-open,
        // so the limiter's own peak is one below that; asserting both ends catches a halved or doubled rate.
        assertTrue("rest peak ${maxInAnyWindow(grants)}", maxInAnyWindow(grants) in (REST_WINDOW_MAX - 1)..REST_WINDOW_MAX)
    }

    @Test
    fun `graphql traffic stays under the 300 per window cap`() = runTest {
        val limiter = RateLimiter(
            nowNanos = { testScheduler.currentTime * nanosPerMilli },
            delayMillis = { delay(it) },
        )
        val grants = mutableListOf<Long>()
        repeat(600) {
            launch {
                limiter.acquire(RateLane.GRAPHQL)
                grants += testScheduler.currentTime
            }
        }
        advanceUntilIdle()
        assertEquals(600, grants.size)
        // Burst 40 plus a refill of 200 per window is the documented ceiling of 240 (peak 239 in a half-open window).
        assertTrue("graphql peak ${maxInAnyWindow(grants)}", maxInAnyWindow(grants) in (GRAPHQL_WINDOW_MAX - 1)..GRAPHQL_WINDOW_MAX)
    }

    @Test
    fun `a blocked limiter waits out Retry-After before granting`() = runTest {
        val limiter = RateLimiter(
            nowNanos = { testScheduler.currentTime * nanosPerMilli },
            delayMillis = { delay(it) },
        )
        limiter.blockFor(RateLane.REST, 20_000L)
        limiter.acquire(RateLane.REST)
        assertEquals(20_000L, testScheduler.currentTime)
    }

    @Test
    fun `a rest block also holds graphql, because graphql spends rest budget`() = runTest {
        val limiter = RateLimiter(
            nowNanos = { testScheduler.currentTime * nanosPerMilli },
            delayMillis = { delay(it) },
        )
        limiter.blockFor(RateLane.REST, 7_000L)
        limiter.acquire(RateLane.GRAPHQL)
        assertEquals(7_000L, testScheduler.currentTime)
    }

    @Test
    fun `a graphql block leaves rest traffic running`() = runTest {
        val limiter = RateLimiter(
            nowNanos = { testScheduler.currentTime * nanosPerMilli },
            delayMillis = { delay(it) },
        )
        limiter.blockFor(RateLane.GRAPHQL, 60_000L)
        limiter.acquire(RateLane.REST)
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test
    fun `budget flow reports remaining tokens and the block`() = runTest {
        val limiter = RateLimiter(
            nowNanos = { testScheduler.currentTime * nanosPerMilli },
            delayMillis = { delay(it) },
        )
        val start = limiter.budget.value
        assertEquals(0L, start.blockedForMillis)
        limiter.acquire(RateLane.GRAPHQL)
        val after = limiter.budget.value
        assertEquals(start.restRemaining - 1, after.restRemaining)
        assertEquals(start.graphQlRemaining - 1, after.graphQlRemaining)
        limiter.blockFor(RateLane.REST, 5_000L)
        assertEquals(5_000L, limiter.budget.value.blockedForMillis)
    }
}
