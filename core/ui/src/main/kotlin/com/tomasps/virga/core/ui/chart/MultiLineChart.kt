package com.tomasps.virga.core.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.tomasps.virga.core.ui.R
import com.tomasps.virga.core.ui.theme.LocalVirgaSemanticColors
import java.util.Locale

/** Axis range shared by every series, so lines are comparable. NaN and infinite values are ignored. */
internal fun sharedDomain(series: List<LineSeries>): ChartDomain =
    ChartMath.domain(series.flatMap { line -> line.points.map { it.value } }, includeZero = false)

/**
 * Several lines on one set of axes with a colour legend. Series are drawn in [palette] order and cycle when
 * there are more series than colours. Lines are matched by position, with x-axis labels taken from the longest
 * series. Empty data shows "No data".
 */
@Composable
fun MultiLineChart(
    series: List<LineSeries>,
    title: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    palette: List<Color> = LocalVirgaSemanticColors.current.chartSeries,
) {
    val cleaned = remember(series) {
        series.map { line -> line.copy(points = line.points.filter { it.value.isFinite() }) }
    }
    val colors = palette.ifEmpty { listOf(MaterialTheme.colorScheme.primary) }
    val domain = remember(cleaned) { sharedDomain(cleaned) }
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labels = remember(cleaned) { cleaned.maxByOrNull { it.points.size }?.points?.map { it.label }.orEmpty() }
    val description = contentDescription ?: multiLineDescription(title, cleaned)
    Column(modifier = modifier) {
        ChartContainer(
            description = description,
            isEmpty = cleaned.all { it.points.isEmpty() },
            modifier = Modifier,
            footer = { AxisLabels(labels) },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawGridLines(gridColor)
                cleaned.forEachIndexed { index, line ->
                    if (line.points.isNotEmpty()) {
                        drawSeriesLine(line.points, domain, colors[index % colors.size])
                    }
                }
            }
        }
        ChartLegend(cleaned.map { it.name }, colors)
    }
}

private fun DrawScope.drawSeriesLine(points: List<ChartPoint>, domain: ChartDomain, color: Color) {
    val inset = LINE_WIDTH.toPx()
    val coordinates = ChartPolyline.build(
        values = points.map { it.value },
        domain = domain,
        left = inset,
        top = inset,
        right = size.width - inset,
        bottom = size.height - inset,
    )
    if (coordinates.size > 1) {
        drawPath(
            path = linePath(coordinates),
            color = color,
            style = Stroke(width = LINE_WIDTH.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
    val (lastX, lastY) = coordinates.last()
    drawCircle(color = color, radius = DOT_RADIUS.toPx(), center = Offset(lastX, lastY))
}

@Composable
private fun multiLineDescription(title: String, series: List<LineSeries>): String {
    val named = series.filter { it.points.isNotEmpty() }
    if (named.isEmpty()) return stringResource(R.string.core_ui_chart_empty, title)
    val count = named.size
    return stringResource(
        R.string.core_ui_chart_summary_multi,
        title,
        pluralStringResource(R.plurals.core_ui_series, count, count),
        named.joinToString(separator = ", ") { line ->
            val last = line.points.last().value
            "${line.name} ${ChartFormat.compact(last, Locale.getDefault())}"
        },
    )
}
