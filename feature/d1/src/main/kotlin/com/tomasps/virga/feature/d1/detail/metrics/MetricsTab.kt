package com.tomasps.virga.feature.d1.detail.metrics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.common.formatBytes
import com.tomasps.virga.core.common.formatCompactCount
import com.tomasps.virga.core.ui.chart.ChartPoint
import com.tomasps.virga.core.ui.chart.LineSeries
import com.tomasps.virga.core.ui.chart.MultiLineChart
import com.tomasps.virga.core.ui.components.OpenInDashboardRow
import com.tomasps.virga.core.ui.components.SectionHeader
import com.tomasps.virga.core.ui.components.StatTile
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.core.ui.state.UiStateContent
import com.tomasps.virga.feature.d1.R
import com.tomasps.virga.feature.d1.data.DailyPoint

/** Callbacks from the metrics tab. */
data class MetricsCallbacks(
    val onRetry: () -> Unit,
    val onOpenInsights: () -> Unit,
)

@Composable
internal fun MetricsTab(state: MetricsUiState, callbacks: MetricsCallbacks) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        SectionHeader(title = stringResource(R.string.d1_metrics_title, METRICS_WINDOW_DAYS.toInt()))
        UiStateContent(state = state.daily, onRetry = callbacks.onRetry) { points ->
            DailyContent(points = points, storage = state.storageBytes)
        }
        SectionHeader(title = stringResource(R.string.d1_metrics_insights_title))
        OpenInDashboardRow(
            description = stringResource(R.string.d1_metrics_insights_description),
            onOpen = callbacks.onOpenInsights,
        )
    }
}

@Composable
private fun DailyContent(points: List<DailyPoint>, storage: UiState<Long>) {
    val reads = points.sumOf { it.rowsRead }.toLong()
    val writes = points.sumOf { it.rowsWritten }.toLong()
    val queries = points.sumOf { it.readQueries + it.writeQueries }.toLong()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StatTile(label = stringResource(R.string.d1_metrics_queries), value = formatCompactCount(queries))
        StatTile(label = stringResource(R.string.d1_metrics_rows_read), value = formatCompactCount(reads))
        StatTile(label = stringResource(R.string.d1_metrics_rows_written), value = formatCompactCount(writes))
        StatTile(
            label = stringResource(R.string.d1_metrics_storage),
            value = (storage as? UiState.Content)?.data?.let { formatBytes(it) } ?: stringResource(R.string.d1_unknown),
        )
        val series = listOf(
            LineSeries(stringResource(R.string.d1_metrics_series_reads), points.map { ChartPoint(it.date, it.rowsRead) }),
            LineSeries(stringResource(R.string.d1_metrics_series_writes), points.map { ChartPoint(it.date, it.rowsWritten) }),
        )
        MultiLineChart(
            series = series,
            title = stringResource(R.string.d1_metrics_chart_title),
            contentDescription = stringResource(R.string.d1_metrics_chart_description, points.size),
        )
    }
}
