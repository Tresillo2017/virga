package com.tomasps.virga.feature.domains.zones

import org.junit.Assert.assertEquals
import org.junit.Test

class ZoneDisplayTest {

    @Test
    fun `an identifier with dashes or underscores reads as a plain phrase`() {
        assertEquals("Pending disabled", humanizeId("pending-disabled"))
        assertEquals("Custom ns", humanizeId("custom_ns"))
    }

    @Test
    fun `a blank identifier stays blank`() {
        assertEquals("", humanizeId("  "))
    }
}
