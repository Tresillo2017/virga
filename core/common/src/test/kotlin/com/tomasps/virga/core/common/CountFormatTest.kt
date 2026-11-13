package com.tomasps.virga.core.common

import org.junit.Test
import org.junit.Assert.assertEquals

class CountFormatTest {
    @Test
    fun `leaves counts under one thousand as plain integers`() {
        assertEquals("0", formatCompactCount(0))
        assertEquals("999", formatCompactCount(999))
    }

    @Test
    fun `formats thousands with two decimals and trims zeros`() {
        assertEquals("125.56k", formatCompactCount(125_560))
        assertEquals("1.2k", formatCompactCount(1_200))
        assertEquals("1k", formatCompactCount(1_000))
    }

    @Test
    fun `rolls over to the next unit when rounding reaches one thousand`() {
        assertEquals("1M", formatCompactCount(999_999))
    }

    @Test
    fun `formats millions billions and trillions`() {
        assertEquals("3.67M", formatCompactCount(3_670_000))
        assertEquals("2.1B", formatCompactCount(2_100_000_000))
        assertEquals("4T", formatCompactCount(4_000_000_000_000))
    }

    @Test
    fun `keeps the sign of negative counts`() {
        assertEquals("-125.56k", formatCompactCount(-125_560))
    }
}
