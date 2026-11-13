package com.tomasps.virga.core.ui.brand

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SplashTimelineTest {

    @Test
    fun `streaks start 100 milliseconds apart`() {
        assertEquals(0, SplashTimeline.streakDelayMs(0))
        assertEquals(100, SplashTimeline.streakDelayMs(1))
        assertEquals(200, SplashTimeline.streakDelayMs(2))
    }

    @Test
    fun `whole splash finishes under the 1100 millisecond limit`() {
        val total = SplashTimeline.totalMs(VirgaMarkGeometry.STREAK_COUNT)

        assertTrue("total was $total ms", total < SplashTimeline.MAX_TOTAL_MS)
        assertEquals(750, total)
    }

    @Test
    fun `wordmark begins after the first streak has finished drawing`() {
        val firstStreakEnd = SplashTimeline.streakEndMs(0)

        assertTrue(SplashTimeline.WORDMARK_START_MS > firstStreakEnd)
    }

    @Test
    fun `wordmark starts before the last streak finishes so the splash does not pause`() {
        val lastStreakEnd = SplashTimeline.streakEndMs(VirgaMarkGeometry.STREAK_COUNT - 1)

        assertTrue(SplashTimeline.WORDMARK_START_MS < lastStreakEnd)
    }

    @Test
    fun `total is zero when there are no streaks and the wordmark still counts`() {
        assertEquals(SplashTimeline.wordmarkEndMs(), SplashTimeline.totalMs(0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative streak index is rejected`() {
        SplashTimeline.streakDelayMs(-1)
    }

    @Test
    fun `a streak is untouched before its delay and fully drawn after its end`() {
        assertEquals(0f, SplashTimeline.streakProgress(1, 99L), 0f)
        assertEquals(1f, SplashTimeline.streakProgress(1, 100L + 360L), 0f)
    }

    @Test
    fun `streak progress only moves forward as time passes`() {
        var previous = -1f
        for (elapsed in 0L..1000L step 16L) {
            val progress = SplashTimeline.streakProgress(0, elapsed)
            assertTrue("went back at $elapsed ms", progress >= previous)
            previous = progress
        }
    }

    @Test
    fun `slow frames still show every streak partly drawn`() {
        // A device that draws every 100 ms still lands on a mid-draw frame for each streak.
        for (index in 0 until VirgaMarkGeometry.STREAK_COUNT) {
            val midDraw = (0L..1000L step 100L).any { elapsed ->
                SplashTimeline.streakProgress(index, elapsed) in 0.01f..0.99f
            }
            assertTrue("streak $index was never mid-draw", midDraw)
        }
    }

    @Test
    fun `the wordmark is hidden at the start and fully shown at the end`() {
        assertEquals(0f, SplashTimeline.wordmarkProgress(0L), 0f)
        assertEquals(1f, SplashTimeline.wordmarkProgress(SplashTimeline.wordmarkEndMs().toLong()), 0f)
    }

    @Test
    fun `hand off happens after the finished picture has been held`() {
        val end = SplashTimeline.totalMs(VirgaMarkGeometry.STREAK_COUNT).toLong() + SplashTimeline.HOLD_MS

        assertEquals(false, SplashTimeline.isHandOffDue(end - 1, VirgaMarkGeometry.STREAK_COUNT))
        assertEquals(true, SplashTimeline.isHandOffDue(end, VirgaMarkGeometry.STREAK_COUNT))
    }

    @Test
    fun `hand off is capped so a long timeline never holds the app back`() {
        val capped = SplashTimeline.handOffMs(streakCount = 50)

        assertEquals(SplashTimeline.MAX_WAIT_MS, capped)
        assertTrue(SplashTimeline.isHandOffDue(SplashTimeline.MAX_WAIT_MS.toLong(), 50))
    }

    @Test
    fun `reduced motion shows the final frame at once`() {
        val finalElapsed = SplashTimeline.handOffMs(VirgaMarkGeometry.STREAK_COUNT).toLong()

        for (index in 0 until VirgaMarkGeometry.STREAK_COUNT) {
            assertEquals(1f, SplashTimeline.streakProgress(index, finalElapsed), 0f)
        }
        assertEquals(1f, SplashTimeline.wordmarkProgress(finalElapsed), 0f)
    }
}
