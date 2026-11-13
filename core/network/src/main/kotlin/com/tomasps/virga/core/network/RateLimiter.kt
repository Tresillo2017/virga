package com.tomasps.virga.core.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

enum class RateLane { REST, GRAPHQL }

/**
 * Snapshot of the shared budget. Counts refresh over time, so this is the value at the last change;
 * a watch loop can read it and slow down when [isLow] holds.
 */
data class RateBudget(
    val restRemaining: Int,
    val restCapacity: Int,
    val graphQlRemaining: Int,
    val graphQlCapacity: Int,
    val blockedForMillis: Long,
) {
    fun isLow(): Boolean = restRemaining * LOW_DIVISOR <= restCapacity

    private companion object {
        const val LOW_DIVISOR = 4
    }
}

/**
 * Token buckets sized so that burst plus one refill per window stays under the Cloudflare limits:
 * REST 150 + 750 = 900 per 5 minutes (limit 1,200); GraphQL 40 + 200 = 240 per 5 minutes (limit 300).
 * GraphQL requests spend from both buckets, so a REST 429 blocks GraphQL too. A GraphQL 429 blocks only the
 * GraphQL lane, so a slow analytics query does not stall REST reads or the watch loop.
 */
class RateLimiter(
    private val nowNanos: () -> Long = { System.nanoTime() },
    private val delayMillis: suspend (Long) -> Unit = { delay(it) },
) {
    private val mutex = Mutex()
    private val rest = TokenBucket(REST_BURST, REST_REFILL_PER_WINDOW, nowNanos())
    private val graphQl = TokenBucket(GRAPHQL_BURST, GRAPHQL_REFILL_PER_WINDOW, nowNanos())
    private var restBlockedUntilNanos: Long = 0L
    private var graphQlBlockedUntilNanos: Long = 0L
    private val mutableBudget = MutableStateFlow(snapshot(nowNanos()))

    val budget: StateFlow<RateBudget> = mutableBudget.asStateFlow()

    /** Suspends until a request on [lane] may be sent. */
    suspend fun acquire(lane: RateLane) {
        while (true) {
            val waitNanos = mutex.withLock { tryTake(lane) }
            if (waitNanos == 0L) return
            delayMillis(ceilMillis(waitNanos))
        }
    }

    /**
     * Blocks [lane] for [millis], as a 429 Retry-After asks. A REST block also holds GraphQL, which spends REST
     * budget. Overlapping blocks keep the later end.
     */
    suspend fun blockFor(lane: RateLane, millis: Long) {
        mutex.withLock {
            val now = nowNanos()
            val end = now + millis * NANOS_PER_MILLI
            when (lane) {
                RateLane.REST -> restBlockedUntilNanos = max(restBlockedUntilNanos, end)
                RateLane.GRAPHQL -> graphQlBlockedUntilNanos = max(graphQlBlockedUntilNanos, end)
            }
            mutableBudget.value = snapshot(now)
        }
    }

    private fun blockedUntil(lane: RateLane): Long = when (lane) {
        RateLane.REST -> restBlockedUntilNanos
        RateLane.GRAPHQL -> max(restBlockedUntilNanos, graphQlBlockedUntilNanos)
    }

    private fun tryTake(lane: RateLane): Long {
        val now = nowNanos()
        rest.refill(now)
        graphQl.refill(now)
        val blockWait = max(0L, blockedUntil(lane) - now)
        val needsGraphQl = lane == RateLane.GRAPHQL
        val tokenWait = max(rest.waitNanosForToken(), if (needsGraphQl) graphQl.waitNanosForToken() else 0L)
        val wait = max(blockWait, tokenWait)
        if (wait == 0L) {
            rest.take()
            if (needsGraphQl) graphQl.take()
        }
        mutableBudget.value = snapshot(now)
        return wait
    }

    private fun snapshot(now: Long): RateBudget {
        rest.refill(now)
        graphQl.refill(now)
        val longestBlock = max(restBlockedUntilNanos, graphQlBlockedUntilNanos)
        return RateBudget(
            restRemaining = rest.available(),
            restCapacity = REST_BURST,
            graphQlRemaining = graphQl.available(),
            graphQlCapacity = GRAPHQL_BURST,
            blockedForMillis = ceilMillis(max(0L, longestBlock - now)),
        )
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
        const val WINDOW_NANOS = 300_000L * NANOS_PER_MILLI
        const val REST_BURST = 150
        const val REST_REFILL_PER_WINDOW = 750
        const val GRAPHQL_BURST = 40
        const val GRAPHQL_REFILL_PER_WINDOW = 200

        /** Rounds up to whole milliseconds; zero stays zero so an idle limiter reports no block. */
        fun ceilMillis(nanos: Long): Long =
            if (nanos <= 0L) 0L else max(1L, ceil(nanos / NANOS_PER_MILLI.toDouble()).toLong())
    }
}

/** Fractional token bucket driven by an injected monotonic clock. Not thread-safe; [RateLimiter] locks it. */
internal class TokenBucket(
    private val capacity: Int,
    refillPerWindow: Int,
    startNanos: Long,
) {
    private val refillPerNano: Double = refillPerWindow.toDouble() / WINDOW_NANOS_DOUBLE
    private var tokens: Double = capacity.toDouble()
    private var lastNanos: Long = startNanos

    fun refill(nowNanos: Long) {
        val elapsed = nowNanos - lastNanos
        if (elapsed <= 0) return
        tokens = min(capacity.toDouble(), tokens + elapsed * refillPerNano)
        lastNanos = nowNanos
    }

    fun available(): Int = floor(tokens).toInt()

    fun waitNanosForToken(): Long =
        if (tokens >= 1.0) 0L else ceil((1.0 - tokens) / refillPerNano).toLong()

    fun take() {
        tokens -= 1.0
    }

    private companion object {
        const val WINDOW_NANOS_DOUBLE = 300_000.0 * 1_000_000.0
    }
}
