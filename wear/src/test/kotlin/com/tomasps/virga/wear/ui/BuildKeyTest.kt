package com.tomasps.virga.wear.ui

import com.tomasps.virga.wear.builds.findByKey
import com.tomasps.virga.wear.builds.routeKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Uri.encode in detailRoute needs the Android framework.
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class BuildKeyTest {
    private val a = build(id = "same").copy(scriptName = "api")
    private val b = build(id = "same").copy(scriptName = "web")
    private val c = build(id = "other").copy(scriptName = "api")

    @Test
    fun keysDifferWhenOnlyTheUuidCollides() {
        assertEquals(3, listOf(a, b, c).map { it.routeKey() }.toSet().size)
    }

    @Test
    fun reorderedListStillFindsTheSameBuild() {
        val before = listOf(a, b, c)
        val after = listOf(c, b, a)
        assertEquals(b, before.findByKey("web", "same"))
        assertEquals(b, after.findByKey("web", "same"))
        assertEquals(a, after.findByKey("api", "same"))
    }

    @Test
    fun missingBuildIsNull() = assertNull(listOf(a).findByKey("web", "same"))

    @Test
    fun detailRouteIsNullWhenAPartIsBlank() {
        assertNull(detailRoute(build(id = "").copy(scriptName = "api")))
        assertNull(detailRoute(build(id = "x").copy(scriptName = " ")))
        assertEquals("build/api/x", detailRoute(build(id = "x").copy(scriptName = "api")))
    }
}
