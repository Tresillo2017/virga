package com.tomasps.virga.feature.domains.zone

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset

class ChartTimeLabelTest {

    @Test
    fun `an API timestamp becomes a short label in the given zone`() {
        // Arrange
        val raw = "2026-11-10T17:21:03.863541Z"

        // Act
        val label = formatChartTimeLabel(raw, ZoneOffset.UTC)

        // Assert
        assertEquals("Nov 10, 17:21", label)
    }

    @Test
    fun `a value that is not a timestamp is returned unchanged`() {
        assertEquals("not a time", formatChartTimeLabel("not a time", ZoneOffset.UTC))
    }
}
