package com.tomasps.virga.feature.kv.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.ui.chart.ChartPoint
import com.tomasps.virga.core.ui.chart.LineChart
import com.tomasps.virga.core.ui.chart.LineSeries
import com.tomasps.virga.core.ui.chart.MultiLineChart
import com.tomasps.virga.core.ui.components.KeyValueRow
import com.tomasps.virga.core.ui.components.SectionCard
import com.tomasps.virga.core.ui.state.EmptyState
import com.tomasps.virga.core.ui.state.UiStateContent
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.core.ui.state.LoadingState
import com.tomasps.virga.feature.kv.R
import com.tomasps.virga.feature.kv.data.KvDailyRequests
import com.tomasps.virga.feature.kv.data.KvMetrics
import com.tomasps.virga.feature.kv.metrics.KvMetricsUiState

private val TAB_PADDING = 16.dp
private const val SHORT_DATE_START = 5

/**
 * Operations per action and storage over the metrics window, both from GraphQL. The charts have text summaries, so a
 * screen reader gets the totals too.
 */
@Composable
internal fun KvMetricsTab(state: KvMetricsUiState, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(TAB_PADDING),
    ) {
        when (val metrics = state.metrics) {
            null, is UiState.Loading -> LoadingState()
            is UiState.Failure -> UiStateContent(state = metrics, onRetry = onRetry) {}
            is UiState.Content -> MetricsContent(metrics.data)
        }
    }
}

@Composable
private fun MetricsContent(metrics: KvMetrics) {
    if (metrics.operations.isEmpty() && metrics.storage.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.kv_metrics_empty_title),
            message = stringResource(R.string.kv_metrics_empty_message),
        )
        return
    }
    val totalRequests = metrics.operations.sumOf { it.requests }
    if (metrics.operations.isNotEmpty()) {
        MultiLineChart(
            series = operationSeries(metrics.operations),
            title = stringResource(R.string.kv_metrics_operations_title),
            contentDescription = stringResource(R.string.kv_metrics_operations_description, totalRequests),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (metrics.storage.isNotEmpty()) {
        val sorted = metrics.storage.sortedBy { it.date }
        val latest = sorted.last()
        Text(
            text = stringResource(R.string.kv_metrics_storage_title),
            modifier = Modifier.padding(top = TAB_PADDING),
        )
        LineChart(
            points = sorted.map { ChartPoint(label = it.date.takeLast(SHORT_DATE_START), value = it.byteCount.toDouble()) },
            title = stringResource(R.string.kv_metrics_storage_bytes),
            contentDescription = stringResource(R.string.kv_metrics_storage_description, latest.keyCount),
            modifier = Modifier.fillMaxWidth(),
        )
        SectionCard(
            rows = listOf(
                { KeyValueRow(key = stringResource(R.string.kv_metrics_latest_keys), value = latest.keyCount.toString()) },
                { KeyValueRow(key = stringResource(R.string.kv_metrics_latest_size), value = formatBytes(latest.byteCount)) },
            ),
            modifier = Modifier.padding(top = TAB_PADDING),
        )
    }
}

/** One line per action, with every day of the window on the x axis and zero where an action had no requests. */
@Composable
private fun operationSeries(operations: List<KvDailyRequests>): List<LineSeries> {
    val dates = operations.map { it.date }.distinct().sorted()
    return operations.groupBy { it.action }.toSortedMap().map { (action, rows) ->
        val byDate = rows.associateBy { it.date }
        LineSeries(
            name = actionLabel(action),
            points = dates.map { date ->
                ChartPoint(label = date.takeLast(SHORT_DATE_START), value = (byDate[date]?.requests ?: 0L).toDouble())
            },
        )
    }
}

@Composable
private fun actionLabel(action: String): String = when (action.lowercase()) {
    "read" -> stringResource(R.string.kv_metric_action_read)
    "write" -> stringResource(R.string.kv_metric_action_write)
    "delete" -> stringResource(R.string.kv_metric_action_delete)
    "list" -> stringResource(R.string.kv_metric_action_list)
    else -> action
}
