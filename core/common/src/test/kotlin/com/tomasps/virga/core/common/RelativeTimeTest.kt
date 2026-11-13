package com.tomasps.virga.core.common

import java.time.Instant
import org.junit.Test
import org.junit.Assert.assertEquals

class RelativeTimeTest {
    private val now: Instant = Instant.parse("2026-11-11T12:00:00Z")

    @Test
    fun `less than a minute ago is just now`() {
        assertEquals(RelativeTime.JustNow, relativeTime(now.minusSeconds(30), now))
    }

    @Test
    fun `reports minutes hours and days in the past`() {
        assertEquals(RelativeTime.Past(5, RelativeUnit.MINUTE), relativeTime(now.minusSeconds(5 * 60), now))
        assertEquals(RelativeTime.Past(3, RelativeUnit.HOUR), relativeTime(now.minusSeconds(3 * 3600), now))
        assertEquals(RelativeTime.Past(2, RelativeUnit.DAY), relativeTime(now.minusSeconds(2 * 86_400), now))
    }

    @Test
    fun `reports future instants`() {
        assertEquals(RelativeTime.Future(5, RelativeUnit.MINUTE), relativeTime(now.plusSeconds(5 * 60), now))
    }

    @Test
    fun `a future instant under a minute away is just now`() {
        assertEquals(RelativeTime.JustNow, relativeTime(now.plusSeconds(10), now))
    }
}
