package com.tomasps.virga.core.ui.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.ui.R

internal val CHART_HEIGHT = 168.dp
internal val CHART_Y_AXIS_WIDTH = 48.dp
private val Y_LABEL_GAP = 6.dp
private val LABEL_TOP_PADDING = 6.dp
private val LEGEND_SWATCH = 10.dp
private val LEGEND_GAP = 6.dp
private val LEGEND_ITEM_GAP = 14.dp

/**
 * Frame shared by every chart. The whole plot is one accessible element carrying [description];
 * the drawing underneath is cleared from the semantics tree. Empty data shows a "No data" label instead.
 * With [yDomain], the top and bottom of the axis are labelled at the left of the plot.
 */
@Composable
internal fun ChartContainer(
    description: String,
    isEmpty: Boolean,
    modifier: Modifier,
    yDomain: ChartDomain? = null,
    footer: @Composable () -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val showYAxis = yDomain != null && !isEmpty
    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth().height(CHART_HEIGHT)) {
            if (showYAxis) YAxisLabels(yDomain)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clearAndSetSemantics { contentDescription = description },
            ) {
                if (isEmpty) {
                    Text(
                        text = stringResource(R.string.core_ui_no_data),
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    content()
                }
            }
        }
        Box(modifier = Modifier.padding(start = if (showYAxis) CHART_Y_AXIS_WIDTH else 0.dp)) {
            footer()
        }
    }
}

/** Maximum at the top of the plot and minimum at the bottom, so the scale of the lines is readable. */
@Composable
private fun YAxisLabels(domain: ChartDomain) {
    val locale = LocalConfiguration.current.locales[0]
    Column(
        modifier = Modifier
            .width(CHART_Y_AXIS_WIDTH)
            .fillMaxHeight()
            .padding(end = Y_LABEL_GAP),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            text = ChartFormat.compact(domain.max, locale),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            text = ChartFormat.compact(domain.min, locale),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** First and last x-axis labels, under the plot. Shows one label when there is only one. */
@Composable
internal fun AxisLabels(labels: List<String>) {
    if (labels.isEmpty()) return
    val first = labels.first()
    val last = labels.last()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = LABEL_TOP_PADDING),
        horizontalArrangement = if (labels.size > 1) Arrangement.SpaceBetween else Arrangement.Center,
    ) {
        Text(
            text = first,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (labels.size > 1) {
            Text(
                text = last,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Accessible summaries built from the data. Wording lives in strings.xml. */
@Composable
internal fun lineDescription(title: String, points: List<ChartPoint>): String {
    if (points.isEmpty()) return stringResource(R.string.core_ui_chart_empty, title)
    val values = points.map { it.value }
    val locale = LocalConfiguration.current.locales[0]
    return stringResource(
        R.string.core_ui_chart_summary_line,
        title,
        pluralStringResource(R.plurals.core_ui_points, points.size, points.size),
        ChartFormat.compact(values.last(), locale),
        ChartFormat.compact(values.min(), locale),
        ChartFormat.compact(values.max(), locale),
    )
}

@Composable
internal fun barDescription(title: String, bars: List<ChartPoint>): String {
    if (bars.isEmpty()) return stringResource(R.string.core_ui_chart_empty, title)
    val largest = bars.maxBy { it.value }
    return stringResource(
        R.string.core_ui_chart_summary_bars,
        title,
        pluralStringResource(R.plurals.core_ui_points, bars.size, bars.size),
        ChartFormat.compact(largest.value, LocalConfiguration.current.locales[0]),
        largest.label,
    )
}

@Composable
internal fun stackedDescription(title: String, columnCount: Int, segmentNames: List<String>): String {
    if (columnCount == 0 || segmentNames.isEmpty()) return stringResource(R.string.core_ui_chart_empty, title)
    return stringResource(
        R.string.core_ui_chart_summary_stacked,
        title,
        pluralStringResource(R.plurals.core_ui_points, columnCount, columnCount),
        segmentNames.joinToString(separator = ", "),
    )
}

/** Colour key for multi-series charts. Swatches cycle through [palette]; each name is one series. */
@Composable
internal fun ChartLegend(names: List<String>, palette: List<Color>) {
    if (names.isEmpty() || palette.isEmpty()) return
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        names.forEachIndexed { index, name ->
            Box(
                modifier = Modifier
                    .size(LEGEND_SWATCH)
                    .background(palette[index % palette.size], CircleShape)
                    .clearAndSetSemantics {},
            )
            Spacer(Modifier.width(LEGEND_GAP))
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(LEGEND_ITEM_GAP))
        }
    }
}
