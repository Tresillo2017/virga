package com.tomasps.virga.wear.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class StartRouteTest {

    @Test
    fun detailRouteIsHonoured() {
        assertEquals("build/api/b-1", startRouteFrom("build/api/b-1"))
    }

    @Test
    fun routeBuiltByDetailRouteRoundTripsForHostileNames() {
        listOf("my worker", "a/b", "q?x", "50%", "h#sh", "{id}", "a b/c?d%e#f{g}").forEach { name ->
            val route = detailRoute(build("b-1").copy(scriptName = name, buildUuid = "$name-1"))

            assertEquals(name, route, startRouteFrom(route))
        }
    }

    @Test
    fun missingBlankAndUnknownRoutesAreIgnored() {
        assertNull(startRouteFrom(null))
        assertNull(startRouteFrom(""))
        assertNull(startRouteFrom("builds"))
        assertNull(startRouteFrom("build/api"))
        assertNull(startRouteFrom("other/a/b"))
        assertNull(startRouteFrom("build//b"))
        assertNull(startRouteFrom("build/a/b/c"))
    }

    @Test
    fun malformedOrNonCanonicalPartsAreRejected() {
        assertNull(startRouteFrom("build/a b/c"))
        assertNull(startRouteFrom("build/a?x=1/c"))
        assertNull(startRouteFrom("build/a#f/c"))
        assertNull(startRouteFrom("build/{script}/c"))
        assertNull(startRouteFrom("build/a%/c"))
        assertNull(startRouteFrom("build/%61/c"))
    }
}
