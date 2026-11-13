package com.tomasps.virga.core.ui.brand

import androidx.compose.animation.core.CubicBezierEasing
import kotlin.math.max
import kotlin.math.min

/**
 * Timing of the animated splash, in milliseconds from the start. Pure data and pure functions so the timeline
 * can be asserted in tests. Streaks draw downward one after another; the wordmark fades in once the last streak
 * is nearly finished.
 *
 * Progress is a function of elapsed time, not of the number of frames drawn: a slow device skips no stage, it
 * just shows each stage at the frame that lands inside it.
 */
object SplashTimeline {
    const val STAGGER_MS = 100
    const val STREAK_DURATION_MS = 360
    const val WORDMARK_START_MS = 450
    const val WORDMARK_DURATION_MS = 300

    /** How long the finished picture stays up before the hand-off, so the wordmark is actually seen. */
    const val HOLD_MS = 350

    /** Upper bound the whole splash must stay under. */
    const val MAX_TOTAL_MS = 1100

    /** The splash hands off at the end of the animation, or at this time, whichever is first. */
    const val MAX_WAIT_MS = 1500

    /**
     * How long the clock waits for the caller to show the splash. The system splash hands over to it with an exit
     * animation; if that signal never comes, the splash plays anyway after this long.
     */
    const val MAX_WAIT_FOR_START_MS = 1500L

    /** Standard Material decelerate curve: fast start, long soft landing. */
    private val decelerate = CubicBezierEasing(0f, 0f, 0.2f, 1f)

    /** Start time of streak [index] (0-based, in streak order). */
    fun streakDelayMs(index: Int): Int {
        require(index >= 0) { "streak index must not be negative: $index" }
        return index * STAGGER_MS
    }

    fun streakEndMs(index: Int): Int = streakDelayMs(index) + STREAK_DURATION_MS

    fun wordmarkEndMs(): Int = WORDMARK_START_MS + WORDMARK_DURATION_MS

    /** Time until the last animation finishes, for [streakCount] streaks. */
    fun totalMs(streakCount: Int): Int {
        val lastStreakEnd = if (streakCount <= 0) 0 else streakEndMs(streakCount - 1)
        return max(lastStreakEnd, wordmarkEndMs())
    }

    /** How drawn streak [index] is after [elapsedMs]: 0 before its delay, 1 once it has finished. */
    fun streakProgress(index: Int, elapsedMs: Long): Float =
        segmentProgress(elapsedMs, startMs = streakDelayMs(index), durationMs = STREAK_DURATION_MS)

    /** How visible the wordmark is after [elapsedMs]. */
    fun wordmarkProgress(elapsedMs: Long): Float =
        segmentProgress(elapsedMs, startMs = WORDMARK_START_MS, durationMs = WORDMARK_DURATION_MS)

    /** Elapsed time at which the splash hands off to the app: the end of the animation plus [HOLD_MS], capped at [MAX_WAIT_MS]. */
    fun handOffMs(streakCount: Int): Int = min(totalMs(streakCount) + HOLD_MS, MAX_WAIT_MS)

    fun isHandOffDue(elapsedMs: Long, streakCount: Int): Boolean = elapsedMs >= handOffMs(streakCount)

    private fun segmentProgress(elapsedMs: Long, startMs: Int, durationMs: Int): Float {
        val fraction = ((elapsedMs - startMs).toFloat() / durationMs).coerceIn(0f, 1f)
        return decelerate.transform(fraction)
    }
}
