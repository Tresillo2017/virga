package com.tomasps.virga.wear.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StartRouteTest {

    @Test
    fun detailRouteIsHonoured() {
        assertEquals("build/api/b-1", startRouteFrom("build/api/b-1"))
    }

    @Test
    fun missingBlankAndUnknownRoutesAreIgnored() {
        assertNull(startRouteFrom(null))
        assertNull(startRouteFrom(""))
        assertNull(startRouteFrom("builds"))
        assertNull(startRouteFrom("build/api"))
        assertNull(startRouteFrom("other/a/b"))
    }
}
