package com.tomasps.virga.feature.home.analytics

import com.tomasps.virga.core.ui.components.TimeRange
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsWindowTest {

    private val now: Instant = Instant.parse("2026-11-11T12:30:00Z")

    @Test
    fun `a 24 hour range compares with the 24 hours before it`() {
        // Arrange and Act
        val window = analyticsWindow(TimeRange.TWENTY_FOUR_HOURS, now)

        // Assert
        assertEquals(now.minus(Duration.ofHours(24)), window.currentStart)
        assertEquals(now.minus(Duration.ofHours(48)), window.previousStart)
        assertTrue(window.hasComparison)
    }

    @Test
    fun `a 7 day range still has an equal previous period`() {
        val window = analyticsWindow(TimeRange.SEVEN_DAYS, now)

        assertEquals(now.minus(Duration.ofDays(14)), window.previousStart)
        assertTrue(window.hasComparison)
    }

    @Test
    fun `a 30 day range has no previous period because two of them exceed retention`() {
        val window = analyticsWindow(TimeRange.THIRTY_DAYS, now)

        assertNull(window.previousStart)
        assertFalse(window.hasComparison)
        assertEquals(now, window.end)
    }

    @Test
    fun `an hour bucket is the start of the hour containing the instant`() {
        assertEquals(
            Instant.parse("2026-11-11T12:00:00Z"),
            hourBucket(Instant.parse("2026-11-11T12:59:59.999Z")),
        )
    }
}
