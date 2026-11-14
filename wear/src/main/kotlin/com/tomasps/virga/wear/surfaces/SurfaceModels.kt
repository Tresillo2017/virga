package com.tomasps.virga.wear.surfaces

import com.tomasps.virga.wear.present.BuildTileModel
import com.tomasps.virga.wear.present.BuildVisualState
import com.tomasps.virga.wear.present.ComplicationModel
import com.tomasps.virga.wear.present.ageText
import com.tomasps.virga.wear.present.complicationFor
import com.tomasps.virga.wear.present.tileFor
import java.time.Instant

internal const val SETUP_SHORT_TEXT = "Setup"
internal const val SETUP_TITLE = "Open phone app"
internal const val RECONNECT_SHORT_TEXT = "Sync"
internal const val RECONNECT_TITLE = "Reconnect from phone"
internal const val NO_DATA_SHORT_TEXT = "None"
internal const val NO_DATA_TITLE = "No data"
private const val AGE_SEPARATOR = " · "

/**
 * The complication for [data]. The complication has no age field, so a stale one carries it in the title
 * (`checkout · 3h`); a missing session or data gets its own wording instead of a build.
 */
fun complicationForSurface(data: SurfaceData, now: Instant): ComplicationModel = when (data.status) {
    SurfaceStatus.NotConnected -> ComplicationModel(SETUP_SHORT_TEXT, SETUP_TITLE, BuildVisualState.UNKNOWN)
    SurfaceStatus.Reconnect -> ComplicationModel(RECONNECT_SHORT_TEXT, RECONNECT_TITLE, BuildVisualState.UNKNOWN)
    SurfaceStatus.Fresh -> complicationFor(data.latest, now)
    SurfaceStatus.Stale -> staleComplication(data, now)
}

private fun staleComplication(data: SurfaceData, now: Instant): ComplicationModel {
    val latest = data.latest ?: return ComplicationModel(NO_DATA_SHORT_TEXT, NO_DATA_TITLE, BuildVisualState.UNKNOWN)
    val base = complicationFor(latest, now)
    val age = data.fetchedAt?.let { ageText(it, now) } ?: return base
    return base.copy(title = base.title + AGE_SEPARATOR + age)
}

/** The tile for [data], with the same wording for a missing session or data as the complication. */
fun tileForSurface(data: SurfaceData, now: Instant): BuildTileModel = when (data.status) {
    SurfaceStatus.NotConnected -> BuildTileModel(SETUP_SHORT_TEXT, BuildVisualState.UNKNOWN, SETUP_TITLE, true)
    SurfaceStatus.Reconnect -> BuildTileModel(RECONNECT_SHORT_TEXT, BuildVisualState.UNKNOWN, RECONNECT_TITLE, true)
    SurfaceStatus.Fresh -> tileFor(data.latest, data.fetchedAt, now)
    SurfaceStatus.Stale ->
        if (data.latest == null) {
            BuildTileModel(NO_DATA_TITLE, BuildVisualState.UNKNOWN, "", true)
        } else {
            tileFor(data.latest, data.fetchedAt, now).copy(isStale = true)
        }
}
