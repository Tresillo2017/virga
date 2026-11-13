package com.tomasps.virga.core.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.ui.theme.LocalVirgaSemanticColors

internal val LINE_WIDTH = 2.dp
internal val DOT_RADIUS = 4.dp
private val GRID_WIDTH = 1.dp
private const val AREA_ALPHA = 0.28f
private const val GRID_FRACTIONS = 3

/**
 * Line chart with a soft area fill. Empty data shows "No data"; a single point is drawn as a dot in the middle.
 * [title] names the series for screen readers. Pass [contentDescription] to replace the generated summary.
 */
@Composable
fun LineChart(
    points: List<ChartPoint>,
    title: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    color: Color = LocalVirgaSemanticColors.current.chartSeries.first(),
) {
    val finite = remember(points) { points.filter { it.value.isFinite() } }
    val domain = remember(finite) { ChartMath.domain(finite.map { it.value }, includeZero = false) }
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val description = contentDescription ?: lineDescription(title, finite)
    ChartContainer(
        description = description,
        isEmpty = finite.isEmpty(),
        modifier = modifier,
        footer = { AxisLabels(finite.map { it.label }) },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawGridLines(gridColor)
            if (finite.isNotEmpty()) drawLineSeries(finite, domain, color)
        }
    }
}

internal fun DrawScope.drawGridLines(color: Color) {
    val width = GRID_WIDTH.toPx()
    repeat(GRID_FRACTIONS) { index ->
        val y = size.height * index / (GRID_FRACTIONS - 1)
        drawLine(color = color, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = width)
    }
}

private fun DrawScope.drawLineSeries(points: List<ChartPoint>, domain: ChartDomain, color: Color) {
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
        drawArea(coordinates, color)
        drawPath(
            path = linePath(coordinates),
            color = color,
            style = Stroke(width = LINE_WIDTH.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
    val (lastX, lastY) = coordinates.last()
    drawCircle(color = color, radius = DOT_RADIUS.toPx(), center = Offset(lastX, lastY))
}

private fun DrawScope.drawArea(coordinates: List<Pair<Float, Float>>, color: Color) {
    val baseline = size.height
    val area = Path().apply {
        moveTo(coordinates.first().first, baseline)
        coordinates.forEach { (x, y) -> lineTo(x, y) }
        lineTo(coordinates.last().first, baseline)
        close()
    }
    drawPath(
        path = area,
        brush = Brush.verticalGradient(
            colors = listOf(color.copy(alpha = AREA_ALPHA), Color.Transparent),
            startY = 0f,
            endY = baseline,
        ),
    )
}

/** Open polyline through the coordinates, shared by the line chart and the sparkline. */
internal fun linePath(coordinates: List<Pair<Float, Float>>): Path = Path().apply {
    coordinates.forEachIndexed { index, (x, y) ->
        if (index == 0) moveTo(x, y) else lineTo(x, y)
    }
}
