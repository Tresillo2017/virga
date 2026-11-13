package com.tomasps.virga.core.ui.chart

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class ChartFormatTest {

    @Test
    fun `values below one thousand show without a suffix`() {
        assertEquals("999", ChartFormat.compact(999.0, Locale.US))
        assertEquals("2.5", ChartFormat.compact(2.5, Locale.US))
        assertEquals("0", ChartFormat.compact(0.0, Locale.US))
    }

    @Test
    fun `thousands millions and billions get a single letter suffix`() {
        assertEquals("1.2K", ChartFormat.compact(1_200.0, Locale.US))
        assertEquals("1K", ChartFormat.compact(1_000.0, Locale.US))
        assertEquals("3.4M", ChartFormat.compact(3_400_000.0, Locale.US))
        assertEquals("5.6B", ChartFormat.compact(5_600_000_000.0, Locale.US))
    }

    @Test
    fun `negative values keep their sign`() {
        assertEquals("-1.5K", ChartFormat.compact(-1_500.0, Locale.US))
    }

    @Test
    fun `decimal separator follows the locale`() {
        assertEquals("1,2K", ChartFormat.compact(1_200.0, Locale.GERMANY))
    }

    @Test
    fun `non finite values show a dash`() {
        assertEquals("-", ChartFormat.compact(Double.NaN, Locale.US))
        assertEquals("-", ChartFormat.compact(Double.NEGATIVE_INFINITY, Locale.US))
    }
}
