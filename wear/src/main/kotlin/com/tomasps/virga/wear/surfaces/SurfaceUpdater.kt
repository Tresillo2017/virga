package com.tomasps.virga.wear.surfaces

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.tomasps.virga.wear.summary.SummaryTileService

/** Asks the system to re-request both tiles and the complication now instead of at their next 15-minute refresh. */
fun interface SurfaceUpdater {
    fun requestUpdates()
}

class SystemSurfaceUpdater(context: Context) : SurfaceUpdater {
    private val appContext = context.applicationContext

    override fun requestUpdates() {
        try {
            val tiles = TileService.getUpdater(appContext)
            tiles.requestUpdate(BuildTileService::class.java)
            tiles.requestUpdate(SummaryTileService::class.java)
            ComplicationDataSourceUpdateRequester
                .create(appContext, ComponentName(appContext, BuildComplicationService::class.java))
                .requestUpdateAll()
        } catch (@Suppress("TooGenericExceptionCaught") error: RuntimeException) {
            // Best effort: the surfaces still refresh on their own schedule. Class name only.
            Log.w("VirgaWear", "Surface update request failed: ${error::class.java.name}")
        }
    }
}
