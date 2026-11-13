package com.tomasps.virga.feature.r2.metrics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.common.formatBytes
import com.tomasps.virga.core.common.formatCompactCount
import com.tomasps.virga.core.ui.chart.BarChart
import com.tomasps.virga.core.ui.chart.ChartPoint
import com.tomasps.virga.core.ui.chart.LineChart
import com.tomasps.virga.core.ui.components.StatTile
import com.tomasps.virga.core.ui.components.TimeRangePicker
import com.tomasps.virga.core.ui.state.EmptyState
import com.tomasps.virga.core.ui.state.UiStateContent
import com.tomasps.virga.feature.r2.R
import com.tomasps.virga.feature.r2.api.R2BucketMetrics
import com.tomasps.virga.feature.r2.api.R2OperationCount
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Operations and stored size for one bucket. The API returns sampled estimates, and the screen says so. */
@Composable
internal fun R2MetricsTab(bucketName: String, refreshKey: Int) {
    val viewModel: R2MetricsViewModel = koinViewModel(parameters = { parametersOf(bucketName) })
    val state by viewModel.state.collectAsState()
    LaunchedEffect(refreshKey) { if (refreshKey > 0) viewModel.retry() }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
    ) {
        TimeRangePicker(
            selected = state.range,
            onSelected = viewModel::selectRange,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        state.metrics?.let { metrics ->
            UiStateContent(state = metrics, onRetry = viewModel::retry) { data ->
                MetricsContent(data)
            }
        }
        Text(
            text = stringResource(R.string.r2_metrics_estimate_note),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun MetricsContent(data: R2BucketMetrics) {
    if (data.operations.isEmpty() && data.storage.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.r2_metrics_empty_title),
            message = stringResource(R.string.r2_metrics_empty_message),
        )
        return
    }
    val totalRequests = data.operations.sumOf { it.requests }
    val latestStored = data.storage.lastOrNull()?.payloadBytes ?: 0L
    val latestObjects = data.storage.lastOrNull()?.objectCount ?: 0L
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        StatTile(
            label = stringResource(R.string.r2_metrics_requests_total),
            value = formatCompactCount(totalRequests),
            modifier = Modifier.fillMaxWidth(),
        )
        StatTile(
            label = stringResource(R.string.r2_metrics_stored_size),
            value = formatBytes(latestStored),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        StatTile(
            label = stringResource(R.string.r2_metrics_object_count),
            value = formatCompactCount(latestObjects),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
    if (data.operations.isNotEmpty()) {
        val chartTitle = stringResource(R.string.r2_metrics_operations_title)
        val summary = data.operations.joinToString { "${it.action}: ${formatCompactCount(it.requests)}" }
        BarChart(
            bars = data.operations.map { ChartPoint(label = it.action, value = it.requests.toDouble()) },
            title = chartTitle,
            contentDescription = "$chartTitle. $summary",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        OperationValues(data.operations)
    }
    if (data.storage.isNotEmpty()) {
        val chartTitle = stringResource(R.string.r2_metrics_storage_title)
        LineChart(
            points = data.storage.map { ChartPoint(label = it.datetime.take(DATE_PREFIX_LENGTH), value = it.payloadBytes.toDouble()) },
            title = chartTitle,
            contentDescription = chartTitle,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

/** Every operation with its count, so each bar has a readable value; the chart footer shows only some action names. */
@Composable
private fun OperationValues(operations: List<R2OperationCount>) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        operations.forEach { operation ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = operation.action, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = formatCompactCount(operation.requests),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

private const val DATE_PREFIX_LENGTH = 10
