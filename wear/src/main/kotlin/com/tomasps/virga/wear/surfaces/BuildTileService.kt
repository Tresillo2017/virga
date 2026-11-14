package com.tomasps.virga.wear.surfaces

import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import com.tomasps.virga.R
import com.tomasps.virga.wear.present.BuildTileModel
import com.tomasps.virga.wear.present.BuildVisualState
import com.tomasps.virga.wear.ui.detailRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future
import org.koin.android.ext.android.inject
import java.time.Instant

/** How long the system may show this tile before asking for a new one. */
internal const val TILE_FRESHNESS_MILLIS = 15L * 60 * 1000
private const val RESOURCES_VERSION = "1"
private const val TITLE_SIZE_SP = 16f
private const val STATE_SIZE_SP = 22f
private const val DETAIL_SIZE_SP = 13f
private const val STALE_ALPHA_MASK = 0xFF888888.toInt()

/**
 * A tile with the newest build: Worker name, state and age. Tapping it opens that build's detail screen, or the list
 * when there is no build. A stale tile is drawn grey so old data is not mistaken for current.
 */
class BuildTileService : TileService() {

    private val refresher: BuildSurfaceRefresher by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        scope.future {
            val data = refresher.refreshOrNull() ?: SurfaceData(null, null, SurfaceStatus.Stale)
            val model = tileForSurface(data, Instant.now())
            val route = data.latest?.takeIf { data.status == SurfaceStatus.Fresh || data.status == SurfaceStatus.Stale }
                ?.let(::detailRoute)
            TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setFreshnessIntervalMillis(TILE_FRESHNESS_MILLIS)
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layoutFor(model, route)))
                .build()
        }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> =
        scope.future { ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build() }

    private fun layoutFor(model: BuildTileModel, route: String?): LayoutElementBuilders.LayoutElement {
        val color = if (model.isStale) STALE_ALPHA_MASK else model.state.argb()
        return LayoutElementBuilders.Column.Builder()
            .setModifiers(openAppModifiers(packageName, route))
            .addContent(tileText(model.workerName, TITLE_SIZE_SP, STALE_ALPHA_MASK.takeIf { model.isStale } ?: WHITE))
            .addContent(tileText(stateLabel(model), STATE_SIZE_SP, color))
            .addContent(tileText(model.detail, DETAIL_SIZE_SP, STALE_ALPHA_MASK))
            .build()
    }

    private fun stateLabel(model: BuildTileModel): String = when (model.state) {
        BuildVisualState.RUNNING -> getString(R.string.status_running)
        BuildVisualState.PASSED -> getString(R.string.status_passed)
        BuildVisualState.FAILED -> getString(R.string.status_failed)
        BuildVisualState.CANCELED -> getString(R.string.status_canceled)
        BuildVisualState.UNKNOWN -> ""
    }

    private fun BuildVisualState.argb(): Int = when (this) {
        BuildVisualState.RUNNING -> BLUE
        BuildVisualState.PASSED -> GREEN
        BuildVisualState.FAILED -> RED
        BuildVisualState.CANCELED, BuildVisualState.UNKNOWN -> GREY
    }

    private companion object {
        const val WHITE = 0xFFFFFFFF.toInt()
        const val BLUE = 0xFF4FC3F7.toInt()
        const val GREEN = 0xFF66BB6A.toInt()
        const val RED = 0xFFEF5350.toInt()
        const val GREY = 0xFFB0B0B0.toInt()
    }
}
