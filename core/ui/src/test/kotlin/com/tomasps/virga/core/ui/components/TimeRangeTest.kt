package com.tomasps.virga.core.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeRangeTest {

    @Test
    fun `windows are offered in ascending length`() {
        assertEquals(
            listOf(1, 6, 24, 168, 720),
            TimeRange.entries.map { it.hours },
        )
    }

    @Test
    fun `seconds equal hours times 3600`() {
        assertEquals(3_600L, TimeRange.LAST_HOUR.seconds)
        assertEquals(604_800L, TimeRange.SEVEN_DAYS.seconds)
        assertEquals(2_592_000L, TimeRange.THIRTY_DAYS.seconds)
    }
}
