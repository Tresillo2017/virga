package com.tomasps.virga.wear.present

import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Test

class AgeTextTest {

    private fun age(d: Duration) = ageText(NOW.minus(d), NOW)

    @Test
    fun `ageBoundaries`() {
        assertEquals("now", age(Duration.ofSeconds(59)))
        assertEquals("1m", age(Duration.ofSeconds(60)))
        assertEquals("59m", age(Duration.ofMinutes(59)))
        assertEquals("1h", age(Duration.ofMinutes(60)))
        assertEquals("23h", age(Duration.ofHours(23)))
        assertEquals("1d", age(Duration.ofHours(24)))
        assertEquals("2d", age(Duration.ofHours(49)))
    }

    @Test
    fun `futureAgeIsNow`() {
        assertEquals("now", ageText(NOW.plusSeconds(300), NOW))
    }
}
