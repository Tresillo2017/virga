package com.tomasps.virga.wear.summary

import org.junit.Assert.assertEquals
import org.junit.Test

class CompactCountTest {

    @Test
    fun smallNumbersAreShownInFull() {
        assertEquals("0", compactCount(0))
        assertEquals("999", compactCount(999))
    }

    @Test
    fun thousandsAndMillionsAreAbbreviatedWithOneDecimal() {
        assertEquals("1k", compactCount(1_000))
        assertEquals("1.2k", compactCount(1_234))
        assertEquals("12.3k", compactCount(12_345))
        assertEquals("123.4k", compactCount(123_456))
        assertEquals("3.4M", compactCount(3_400_000))
        assertEquals("2.5B", compactCount(2_500_000_000))
    }

    @Test
    fun digitsAreTruncatedNotRounded() {
        assertEquals("1k", compactCount(1_049))
        assertEquals("1.1k", compactCount(1_100))
        assertEquals("1k", compactCount(1_050))
        assertEquals("1M", compactCount(1_000_000))
        assertEquals("999.9k", compactCount(999_999))
    }

    @Test
    fun roundingNeverPromotesPastTheUnit() {
        assertEquals("999.9k", compactCount(999_999))
    }

    /** By design: a negative count is not something to alarm a wearer with, so it reads as an ordinary 0. */
    @Test
    fun negativeValuesShowZero() {
        assertEquals("0", compactCount(-5))
        assertEquals("0", compactCount(Long.MIN_VALUE))
    }

    @Test
    fun absurdValuesAreCapped() {
        assertEquals("999T+", compactCount(Long.MAX_VALUE))
        assertEquals("1T", compactCount(1_000_000_000_000))
    }
}
