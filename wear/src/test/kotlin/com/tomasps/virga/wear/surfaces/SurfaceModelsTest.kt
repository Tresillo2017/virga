package com.tomasps.virga.wear.surfaces

import com.tomasps.virga.wear.present.BuildVisualState
import com.tomasps.virga.wear.ui.build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SurfaceModelsTest {

    private val now = Instant.parse("2026-11-14T12:00:00Z")
    private val fetched = now.minusSeconds(3 * 3600)
    private val latest = build("b-1").copy(scriptName = "checkout")

    @Test
    fun freshComplicationUsesTheBuildAsIs() {
        val model = complicationForSurface(SurfaceData(latest, now, SurfaceStatus.Fresh), now)

        assertEquals("OK", model.shortText)
        assertEquals("checkout", model.title)
        assertEquals(BuildVisualState.PASSED, model.state)
    }

    @Test
    fun staleComplicationKeepsShortTextAndSuffixesTheAgeOnTheTitle() {
        val model = complicationForSurface(SurfaceData(latest, fetched, SurfaceStatus.Stale), now)

        assertEquals("OK", model.shortText)
        assertEquals("checkout · 3h", model.title)
    }

    @Test
    fun staleComplicationWithoutDataSaysNoData() {
        val model = complicationForSurface(SurfaceData(null, null, SurfaceStatus.Stale), now)

        assertEquals(NO_DATA_TITLE, model.title)
        assertEquals("No data", model.title)
    }

    @Test
    fun notConnectedComplicationPointsAtThePhone() {
        val model = complicationForSurface(SurfaceData(null, null, SurfaceStatus.NotConnected), now)

        assertEquals("Setup", model.shortText)
        assertEquals("Open phone app", model.title)
    }

    @Test
    fun reconnectComplicationAsksToReconnect() {
        val model = complicationForSurface(SurfaceData(null, null, SurfaceStatus.Reconnect), now)

        assertEquals("Sync", model.shortText)
        assertEquals("Reconnect from phone", model.title)
    }

    @Test
    fun everyComplicationShortTextFitsSixCharacters() {
        val statuses = listOf(SurfaceStatus.Fresh, SurfaceStatus.Stale, SurfaceStatus.NotConnected, SurfaceStatus.Reconnect)
        statuses.forEach { status ->
            listOf(null, latest).forEach { b ->
                val model = complicationForSurface(SurfaceData(b, b?.let { fetched }, status), now)
                assertTrue(model.shortText, model.shortText.length <= 6)
            }
        }
    }

    @Test
    fun staleTileIsMarkedStaleEvenWhenJustFetchedBefore() {
        val model = tileForSurface(SurfaceData(latest, now, SurfaceStatus.Stale), now)

        assertTrue(model.isStale)
        assertEquals("checkout", model.workerName)
    }

    @Test
    fun freshTileIsNotStale() {
        assertFalse(tileForSurface(SurfaceData(latest, now, SurfaceStatus.Fresh), now).isStale)
    }

    @Test
    fun tilesForMissingSessionsAndDataUseTheSurfaceTexts() {
        assertEquals("Open phone app", tileForSurface(SurfaceData(null, null, SurfaceStatus.NotConnected), now).detail)
        assertEquals("Reconnect from phone", tileForSurface(SurfaceData(null, null, SurfaceStatus.Reconnect), now).detail)
        assertEquals("No data", tileForSurface(SurfaceData(null, null, SurfaceStatus.Stale), now).workerName)
    }
}
