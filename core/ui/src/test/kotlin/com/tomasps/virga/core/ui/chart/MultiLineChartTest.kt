package com.tomasps.virga.core.ui.chart

import org.junit.Assert.assertEquals
import org.junit.Test

class MultiLineChartTest {

    @Test
    fun `shared domain spans the lowest and highest value across all series`() {
        val series = listOf(
            LineSeries("a", listOf(ChartPoint("1", 1.0), ChartPoint("2", 5.0))),
            LineSeries("b", listOf(ChartPoint("1", -3.0), ChartPoint("2", 2.0))),
        )

        val domain = sharedDomain(series)

        assertEquals(-3.0, domain.min, 0.0)
        assertEquals(5.0, domain.max, 0.0)
    }

    @Test
    fun `shared domain ignores non-finite values`() {
        val series = listOf(
            LineSeries("a", listOf(ChartPoint("1", Double.NaN), ChartPoint("2", 4.0), ChartPoint("3", 8.0))),
        )

        val domain = sharedDomain(series)

        assertEquals(4.0, domain.min, 0.0)
        assertEquals(8.0, domain.max, 0.0)
    }

    @Test
    fun `shared domain of no series falls back to the empty range`() {
        val domain = sharedDomain(emptyList())

        assertEquals(0.0, domain.min, 0.0)
        assertEquals(1.0, domain.max, 0.0)
    }
}
