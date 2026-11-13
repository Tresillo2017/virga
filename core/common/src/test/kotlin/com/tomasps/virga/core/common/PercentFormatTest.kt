package com.tomasps.virga.core.common

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

class PercentFormatTest {
    @Test
    fun `formats a percentage with two decimals`() {
        assertEquals("14.10%", formatPercent(14.1))
        assertEquals("0.00%", formatPercent(0.0))
    }

    @Test
    fun `shows a dash for non-finite values`() {
        assertEquals("--", formatPercent(Double.NaN))
        assertEquals("--", formatPercent(Double.POSITIVE_INFINITY))
    }

    @Test
    fun `computes percent change against the previous value`() {
        assertEquals(50.0, percentChange(previous = 100.0, current = 150.0))
        assertEquals(-25.0, percentChange(previous = 200.0, current = 150.0))
    }

    @Test
    fun `returns null percent change when the previous value is zero`() {
        assertNull(percentChange(previous = 0.0, current = 10.0))
    }

    @Test
    fun `formats signed deltas with an explicit plus or minus`() {
        assertEquals("+12.50%", formatSignedPercent(12.5))
        assertEquals("-3.00%", formatSignedPercent(-3.0))
        assertEquals("0.00%", formatSignedPercent(0.0))
        assertEquals("0.00%", formatSignedPercent(-0.001))
    }
}
