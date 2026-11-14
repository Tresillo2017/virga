package com.tomasps.virga.feature.home.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.common.formatCompactCount
import com.tomasps.virga.core.common.formatPercent
import com.tomasps.virga.core.common.formatSignedPercent
import com.tomasps.virga.core.ui.components.DeltaTone
import com.tomasps.virga.core.ui.components.OpenInDashboardRow
import com.tomasps.virga.core.ui.components.SectionCard
import com.tomasps.virga.core.ui.components.StatDelta
import com.tomasps.virga.core.ui.components.StatTile
import com.tomasps.virga.core.ui.components.TimeRange
import com.tomasps.virga.core.ui.state.ErrorState
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.feature.home.R
import com.tomasps.virga.feature.home.analytics.AnalyticsIssue
import com.tomasps.virga.feature.home.analytics.HomeAnalytics
import com.tomasps.virga.feature.home.analytics.Metric
import com.tomasps.virga.feature.home.analytics.MetricKind
import com.tomasps.virga.feature.home.analytics.pointChange
import com.tomasps.virga.feature.home.analytics.relativeChange
import java.util.Locale
import kotlin.math.abs

private const val MIN_SPARKLINE_POINTS = 3
private const val CHANGE_EPSILON = 0.005
private const val TILE_COLUMNS = 2
private const val DURATION_DECIMALS = "%.1f"
/** Notes sit on the card edge, the same as the section headers above them. */
private val NOTE_HORIZONTAL_PADDING = 0.dp
private val INFO_BUTTON_SIZE = 40.dp

/** One tile's spec, resolved to strings and values in the composable. */
private data class TileSpec(
    val label: String,
    val metric: Metric,
    val kind: MetricKind,
    val higherIsWorse: Boolean,
)

@Composable
internal fun HomeAnalyticsSection(
    analytics: UiState<HomeAnalytics>?,
    range: TimeRange,
    workersKnownEmpty: Boolean,
    zonesKnownEmpty: Boolean,
    onOpenDashboard: () -> Unit,
) {
    if (analytics == null) return
    when (analytics) {
        UiState.Loading -> LoadingRow()
        is UiState.Failure -> ErrorState(error = analytics.error)
        is UiState.Content -> AnalyticsContent(
            data = analytics.data,
            workersKnownEmpty = workersKnownEmpty,
            zonesKnownEmpty = zonesKnownEmpty,
            onOpenDashboard = onOpenDashboard,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadingRow() {
    val label = stringResource(R.string.home_section_analytics)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).semantics { contentDescription = label },
        horizontalArrangement = Arrangement.Center,
    ) {
        LoadingIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun AnalyticsContent(
    data: HomeAnalytics,
    workersKnownEmpty: Boolean,
    zonesKnownEmpty: Boolean,
    onOpenDashboard: () -> Unit,
) {
    var showCpuInfo by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TileGrid(tiles = tileSpecs(data), hasComparison = data.hasComparison)
        SampledCaveat(onInfo = { showCpuInfo = true })
        if (!data.hasComparison) {
            NoteText(stringResource(R.string.home_analytics_no_comparison))
        }
        ZoneCoverageNotes(data = data, zonesKnownEmpty = zonesKnownEmpty)
        if (workersKnownEmpty) {
            NoteText(stringResource(R.string.home_analytics_no_workers))
        }
        data.workersIssue?.let { IssueText(it) }
        data.zonesIssue?.let { IssueText(it) }
        if (data.workerCountsApproximate) {
            NoteText(stringResource(R.string.home_analytics_top_approximate))
        }
        SectionCard(
            rows = listOf({
                OpenInDashboardRow(
                    label = stringResource(R.string.home_build_minutes_title),
                    description = stringResource(R.string.home_build_minutes_description),
                    onOpen = onOpenDashboard,
                )
            }),
        )
    }
    if (showCpuInfo) {
        AlertDialog(
            onDismissRequest = { showCpuInfo = false },
            title = { Text(stringResource(R.string.home_cpu_info_title)) },
            text = { Text(stringResource(R.string.home_cpu_info_message)) },
            confirmButton = {
                TextButton(onClick = { showCpuInfo = false }) { Text(stringResource(R.string.home_dismiss)) }
            },
        )
    }
}

/** The one-line caveat about the numbers, quiet on purpose. The info button explains why only P50 and P99 appear. */
@Composable
private fun SampledCaveat(onInfo: () -> Unit) {
    val description = stringResource(R.string.home_cpu_info_description)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.home_analytics_sampled),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = NOTE_HORIZONTAL_PADDING).weight(1f),
        )
        IconButton(onClick = onInfo, modifier = Modifier.size(INFO_BUTTON_SIZE).semantics { contentDescription = description }) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NoteText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = NOTE_HORIZONTAL_PADDING),
    )
}

@Composable
private fun ZoneCoverageNotes(data: HomeAnalytics, zonesKnownEmpty: Boolean) {
    when {
        zonesKnownEmpty -> NoteText(stringResource(R.string.home_analytics_no_zones))
        data.zonesTotal > data.zonesQueried && data.zonesQueried > 0 -> NoteText(
            stringResource(R.string.home_analytics_zones_coverage, data.zonesQueried, data.zonesTotal),
        )
    }
}

/** A failed or missing-permission source. It names the permission the token needs, so the user can add it. */
@Composable
private fun IssueText(issue: AnalyticsIssue) {
    val text = when (issue) {
        is AnalyticsIssue.MissingPermission -> stringResource(R.string.home_analytics_missing_permission, issue.permission)
        AnalyticsIssue.RateLimited -> stringResource(R.string.home_analytics_rate_limited)
        AnalyticsIssue.Failed -> stringResource(R.string.home_analytics_failed)
        AnalyticsIssue.Partial -> stringResource(R.string.home_analytics_partial)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(horizontal = NOTE_HORIZONTAL_PADDING),
    )
}

@Composable
private fun tileSpecs(data: HomeAnalytics): List<TileSpec> = listOf(
    TileSpec(stringResource(R.string.home_tile_requests), data.requests, MetricKind.COUNT, higherIsWorse = false),
    TileSpec(stringResource(R.string.home_tile_cache_hit), data.cacheHitRate, MetricKind.RATIO, higherIsWorse = false),
    TileSpec(stringResource(R.string.home_tile_invocations), data.invocations, MetricKind.COUNT, higherIsWorse = false),
    TileSpec(stringResource(R.string.home_tile_errors), data.errors, MetricKind.COUNT, higherIsWorse = true),
    TileSpec(stringResource(R.string.home_tile_cpu_p50), data.cpuP50Ms, MetricKind.DURATION, higherIsWorse = true),
    TileSpec(stringResource(R.string.home_tile_cpu_p99), data.cpuP99Ms, MetricKind.DURATION, higherIsWorse = true),
)

@Composable
private fun TileGrid(tiles: List<TileSpec>, hasComparison: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.chunked(TILE_COLUMNS).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                pair.forEach { spec ->
                    StatTile(
                        label = spec.label,
                        value = formatValue(spec.metric.current, spec.kind),
                        delta = deltaFor(spec, hasComparison),
                        sparkline = spec.metric.series.takeIf { it.size >= MIN_SPARKLINE_POINTS },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun formatValue(value: Double?, kind: MetricKind): String = when {
    value == null -> stringResource(R.string.home_no_value)
    kind == MetricKind.COUNT -> formatCompactCount(value.toLong())
    kind == MetricKind.RATIO -> formatPercent(value)
    else -> stringResource(R.string.home_unit_ms, String.format(Locale.ROOT, DURATION_DECIMALS, value))
}

@Composable
private fun deltaFor(spec: TileSpec, hasComparison: Boolean): StatDelta? {
    if (!hasComparison) return null
    val isRatio = spec.kind == MetricKind.RATIO
    val change = if (isRatio) spec.metric.pointChange() else spec.metric.relativeChange()
    if (change == null) return null
    val tone = when {
        abs(change) < CHANGE_EPSILON -> DeltaTone.Neutral
        (change > 0) == spec.higherIsWorse -> DeltaTone.Negative
        else -> DeltaTone.Positive
    }
    val text = if (isRatio) {
        stringResource(R.string.home_delta_points, String.format(Locale.ROOT, "%+.2f", change))
    } else {
        stringResource(R.string.home_delta_vs_previous, formatSignedPercent(change))
    }
    return StatDelta(text = text, tone = tone)
}
