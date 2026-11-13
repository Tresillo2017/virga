package com.tomasps.virga.feature.workers.detail

import org.junit.Assert.assertEquals
import org.junit.Test

class DeploymentPercentFormatTest {

    @Test
    fun `whole percentages show without a decimal point`() {
        assertEquals("100", DeploymentSplit.formatPercent(100.0))
        assertEquals("33", DeploymentSplit.formatPercent(33.0))
    }

    @Test
    fun `fractional shares keep their decimals instead of truncating`() {
        assertEquals("33.5", DeploymentSplit.formatPercent(33.5))
    }

    @Test
    fun `shares are rounded to two decimals with trailing zeros trimmed`() {
        assertEquals("33.33", DeploymentSplit.formatPercent(33.333))
        assertEquals("0.01", DeploymentSplit.formatPercent(0.01))
    }
}
