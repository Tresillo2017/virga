package com.tomasps.virga.core.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.tomasps.virga.core.ui.theme.LocalVirgaSemanticColors

private const val COLUMN_FILL_FRACTION = 0.6f

/**
 * Stacked column chart with a legend. Segment colours cycle through [segmentColors].
 * Each column is normalised to [segmentNames] (see [ChartMath.normalizeSegments]): missing segments count as zero,
 * extra ones are ignored, and negative values are drawn as zero.
 */
@Composable
fun StackedBarChart(
    columns: List<StackedColumn>,
    segmentNames: List<String>,
    title: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    segmentColors: List<Color> = LocalVirgaSemanticColors.current.chartSeries,
) {
    val normalized = remember(columns, segmentNames) {
        columns.map { ChartMath.normalizeSegments(it.segments, segmentNames.size) }
    }
    val domain = remember(normalized) {
        ChartMath.domain(ChartMath.stackedTotals(normalized), includeZero = true)
    }
    val palette = segmentColors.ifEmpty { listOf(MaterialTheme.colorScheme.primary) }
    val description = contentDescription ?: stackedDescription(title, columns.size, segmentNames)
    Column(modifier = modifier) {
        ChartContainer(
            description = description,
            isEmpty = columns.isEmpty() || segmentNames.isEmpty(),
            modifier = Modifier,
            footer = { AxisLabels(columns.map { it.label }) },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawStackedColumns(normalized, domain, palette)
            }
        }
        ChartLegend(segmentNames, palette)
    }
}

private fun DrawScope.drawStackedColumns(
    columns: List<List<Double>>,
    domain: ChartDomain,
    palette: List<Color>,
) {
    if (columns.isEmpty()) return
    val slot = size.width / columns.size
    val columnWidth = slot * COLUMN_FILL_FRACTION
    columns.forEachIndexed { columnIndex, segments ->
        val spans = ChartMath.stackSpans(segments, domain, 0f, size.height)
        spans.forEachIndexed { segmentIndex, (top, bottom) ->
            val height = bottom - top
            if (height <= 0f) return@forEachIndexed
            drawRect(
                color = palette[segmentIndex % palette.size],
                topLeft = Offset(columnIndex * slot + (slot - columnWidth) / 2f, top),
                size = Size(columnWidth, height),
            )
        }
    }
}
