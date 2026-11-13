package com.tomasps.virga.feature.builds.watch

import com.tomasps.virga.core.network.RateBudget

/**
 * Timing rules for the watch loop, in one place so the engine and its tests share them. All values are
 * milliseconds. The shared budget is 1,200 REST requests per 5 minutes per user, so a watch that polls every
 * 5 seconds costs 60 requests per 5 minutes per build.
 */
object WatchPolicy {
    const val BASE_INTERVAL_MS = 5_000L
    const val LOW_BUDGET_INTERVAL_MS = 15_000L
    const val MIN_BACKOFF_MS = 5_000L
    const val MAX_BACKOFF_MS = 60_000L
    const val MAX_RETRY_AFTER_MS = 600_000L

    /** A watch session ends after this long, well inside the platform's dataSync budget. */
    const val MAX_WATCH_MS = 2L * 60L * 60L * 1_000L

    /** The service wakes at least this often, and at most this often, however far off the next poll is. */
    const val MIN_LOOP_DELAY_MS = 1_000L
    const val MAX_LOOP_DELAY_MS = 5_000L

    /** Poll interval for the next read. Slows down when the shared budget is low or a block is in force. */
    fun intervalMillis(budget: RateBudget?): Long =
        if (budget != null && (budget.isLow() || budget.blockedForMillis > 0)) LOW_BUDGET_INTERVAL_MS else BASE_INTERVAL_MS

    /**
     * Wait after a failed read. A 429 uses its Retry-After (bounded). Other failures double the wait from
     * [MIN_BACKOFF_MS] up to [MAX_BACKOFF_MS].
     */
    fun backoffAfterError(currentBackoffMs: Long, retryAfterMs: Long?): Long = when {
        retryAfterMs != null -> retryAfterMs.coerceIn(MIN_BACKOFF_MS, MAX_RETRY_AFTER_MS)
        currentBackoffMs <= 0L -> MIN_BACKOFF_MS
        else -> (currentBackoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
    }

    fun isExpired(startedAtMillis: Long, nowMillis: Long): Boolean = nowMillis - startedAtMillis >= MAX_WATCH_MS

    /** How long the service sleeps before the next tick, given the engine's delay to the next due poll. */
    fun loopDelayMillis(nextDueInMillis: Long?): Long =
        (nextDueInMillis ?: MAX_LOOP_DELAY_MS).coerceIn(MIN_LOOP_DELAY_MS, MAX_LOOP_DELAY_MS)
}
