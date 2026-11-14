package com.tomasps.virga.wear.summary

import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import com.tomasps.virga.R
import com.tomasps.virga.wear.surfaces.TILE_FRESHNESS_MILLIS
import com.tomasps.virga.wear.surfaces.openAppModifiers
import com.tomasps.virga.wear.surfaces.tileText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future
import org.koin.android.ext.android.inject
import java.time.Instant

private const val RESOURCES_VERSION = "1"
private const val TITLE_SIZE_SP = 14f
private const val NUMBER_SIZE_SP = 22f
private const val LABEL_SIZE_SP = 12f
private const val MESSAGE_SIZE_SP = 16f
private const val WHITE = 0xFFFFFFFF.toInt()
private const val GREY = 0xFF888888.toInt()
private const val RED = 0xFFEF5350.toInt()

/**
 * A tile with the account's requests and errors for the last 24 hours. Tapping it opens the app's home, the build
 * list. Stale numbers are drawn grey with their age; a missing session or missing analytics access shows a message
 * instead of numbers.
 */
class SummaryTileService : TileService() {

    private val refresher: SummaryRefresher by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        scope.future {
            val surface = refresher.refreshOrNull() ?: SummarySurface(null, null, SummaryStatus.Stale)
            val model = summaryTileFor(surface, Instant.now())
            TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setFreshnessIntervalMillis(TILE_FRESHNESS_MILLIS)
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layoutFor(model)))
                .build()
        }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> =
        scope.future { ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build() }

    private fun layoutFor(model: SummaryTileModel): LayoutElementBuilders.LayoutElement {
        val column = LayoutElementBuilders.Column.Builder().setModifiers(openAppModifiers(packageName, route = null))
        column.addContent(tileText(getString(R.string.summary_title), TITLE_SIZE_SP, WHITE))
        when (model.kind) {
            SummaryTileKind.Numbers -> addNumbers(column, model)
            SummaryTileKind.NotConnected -> column.addContent(message(R.string.summary_open_phone, WHITE))
            SummaryTileKind.Reconnect -> column.addContent(message(R.string.summary_reconnect, WHITE))
            SummaryTileKind.MissingAccess -> column.addContent(message(R.string.summary_no_access, RED))
            SummaryTileKind.NoData -> column.addContent(message(R.string.summary_no_data, GREY))
        }
        return column.build()
    }

    private fun addNumbers(column: LayoutElementBuilders.Column.Builder, model: SummaryTileModel) {
        val color = if (model.isStale) GREY else WHITE
        column.addContent(tileText(model.requests, NUMBER_SIZE_SP, color))
        column.addContent(tileText(getString(R.string.summary_requests), LABEL_SIZE_SP, GREY))
        column.addContent(tileText(model.errors, NUMBER_SIZE_SP, if (model.isStale) GREY else RED))
        column.addContent(tileText(getString(R.string.summary_errors), LABEL_SIZE_SP, GREY))
        model.age?.let { column.addContent(tileText(it, LABEL_SIZE_SP, GREY)) }
    }

    private fun message(resource: Int, color: Int) = tileText(getString(resource), MESSAGE_SIZE_SP, color)
}
