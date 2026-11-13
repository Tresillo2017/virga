package com.tomasps.virga.feature.workers.metrics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.common.formatCompactCount
import com.tomasps.virga.core.ui.chart.ChartPoint
import com.tomasps.virga.core.ui.chart.LineSeries
import com.tomasps.virga.core.ui.chart.MultiLineChart
import com.tomasps.virga.core.ui.components.ListRow
import com.tomasps.virga.core.ui.components.SectionHeader
import com.tomasps.virga.core.ui.components.StatTile
import com.tomasps.virga.core.ui.components.TimeRangePicker
import com.tomasps.virga.core.ui.state.UiStateContent
import com.tomasps.virga.feature.workers.R
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private val SECTION_SPACING = 12.dp
private val SCREEN_PADDING = 16.dp

/**
 * Metrics tab for one Worker. Owns its own ViewModel, so the Overview load never waits on metrics. The whole tab is a
 * single LazyColumn; the metrics body is plain Column content, so nothing nests a scrollable inside another.
 */
@Composable
internal fun WorkerMetricsTab(scriptName: String) {
    val viewModel: WorkerMetricsViewModel = koinViewModel(parameters = { parametersOf(scriptName) })
    val state by viewModel.state.collectAsState()
    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = SCREEN_PADDING),
        verticalArrangement = Arrangement.spacedBy(SECTION_SPACING),
    ) {
        item { TimeRangePicker(selected = state.range, onSelected = viewModel::selectRange) }
        item {
            UiStateContent(state = state.content, onRetry = viewModel::retry) { metrics ->
                MetricsBody(metrics)
            }
        }
    }
}

@Composable
private fun MetricsBody(metrics: WorkerMetrics) {
    val requestsLabel = stringResource(R.string.workers_metrics_requests)
    val errorsLabel = stringResource(R.string.workers_metrics_errors)
    Column(verticalArrangement = Arrangement.spacedBy(SECTION_SPACING)) {
        if (metrics.isTruncated) ListRow(headline = stringResource(R.string.workers_metrics_truncated))
        StatTile(label = requestsLabel, value = formatCompactCount(metrics.totalRequests.toLong()))
        StatTile(label = errorsLabel, value = formatCompactCount(metrics.totalErrors.toLong()))
        StatTile(
            label = stringResource(R.string.workers_metrics_subrequests),
            value = formatCompactCount(metrics.totalSubrequests.toLong()),
        )
        if (metrics.buckets.isEmpty()) {
            ListRow(headline = stringResource(R.string.workers_metrics_empty))
        } else {
            MultiLineChart(
                series = listOf(
                    LineSeries(requestsLabel, metrics.buckets.map { ChartPoint(formatBucketLabel(it.time), it.requests) }),
                    LineSeries(errorsLabel, metrics.buckets.map { ChartPoint(formatBucketLabel(it.time), it.errors) }),
                ),
                title = stringResource(R.string.workers_metrics_requests_chart),
                contentDescription = stringResource(R.string.workers_metrics_requests_chart_description),
            )
            MultiLineChart(
                series = listOf(
                    LineSeries(
                        stringResource(R.string.workers_metrics_cpu_p50),
                        chartPoints(metrics.buckets) { it.cpuP50Us },
                    ),
                    LineSeries(
                        stringResource(R.string.workers_metrics_cpu_p99),
                        chartPoints(metrics.buckets) { it.cpuP99Us },
                    ),
                ),
                title = stringResource(R.string.workers_metrics_cpu_chart),
                contentDescription = stringResource(R.string.workers_metrics_cpu_chart_description),
            )
        }
        SectionHeader(title = stringResource(R.string.workers_metrics_statuses))
        metrics.statuses.forEach { status ->
            ListRow(
                headline = status.status,
                trailing = { Text(formatCompactCount(status.requests.toLong())) },
            )
        }
    }
}

/** One point per bucket that has a value. A missing value is left out, so it never reads as zero CPU time. */
internal fun chartPoints(buckets: List<MetricsBucket>, value: (MetricsBucket) -> Double?): List<ChartPoint> =
    buckets.mapNotNull { bucket -> value(bucket)?.let { ChartPoint(formatBucketLabel(bucket.time), it) } }
