package com.tomasps.virga.core.common

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows

class DurationFormatTest {
    @Test
    fun `formats sub-second durations in milliseconds`() {
        assertEquals("850 ms", formatDuration(850))
    }

    @Test
    fun `formats seconds with one decimal and trims zero`() {
        assertEquals("4.2 s", formatDuration(4_200))
        assertEquals("4 s", formatDuration(4_000))
    }

    @Test
    fun `formats minutes with zero padded seconds`() {
        assertEquals("1m 05s", formatDuration(65_000))
    }

    @Test
    fun `formats hours with zero padded minutes`() {
        assertEquals("2h 03m", formatDuration(7_380_000))
    }

    @Test
    fun `formats days with hours`() {
        assertEquals("1d 4h", formatDuration(100_800_000))
    }

    @Test
    fun `rejects negative durations`() {
        assertThrows(IllegalArgumentException::class.java) { formatDuration(-1) }
    }
}
