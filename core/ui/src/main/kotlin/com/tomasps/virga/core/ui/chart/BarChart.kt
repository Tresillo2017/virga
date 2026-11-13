package com.tomasps.virga.core.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.ui.theme.LocalVirgaSemanticColors

private const val BAR_FILL_FRACTION = 0.6f
private val BAR_CORNER = 4.dp
private val BASELINE_WIDTH = 1.dp

/**
 * Vertical bar chart. The value range always includes zero, so negative values hang below the baseline.
 * Empty data shows "No data".
 */
@Composable
fun BarChart(
    bars: List<ChartPoint>,
    title: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    color: Color = LocalVirgaSemanticColors.current.chartSeries.first(),
) {
    val finite = remember(bars) { bars.filter { it.value.isFinite() } }
    val domain = remember(finite) { ChartMath.domain(finite.map { it.value }, includeZero = true) }
    val baselineColor = MaterialTheme.colorScheme.outlineVariant
    val description = contentDescription ?: barDescription(title, finite)
    ChartContainer(
        description = description,
        isEmpty = finite.isEmpty(),
        modifier = modifier,
        footer = { AxisLabels(finite.map { it.label }) },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawBaseline(domain, baselineColor)
            drawBars(finite, domain, color)
        }
    }
}

private fun DrawScope.drawBaseline(domain: ChartDomain, color: Color) {
    val y = ChartMath.yPixel(0.0, domain, 0f, size.height)
    drawLine(color = color, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = BASELINE_WIDTH.toPx())
}

private fun DrawScope.drawBars(bars: List<ChartPoint>, domain: ChartDomain, color: Color) {
    if (bars.isEmpty()) return
    val slot = size.width / bars.size
    val barWidth = slot * BAR_FILL_FRACTION
    bars.forEachIndexed { index, bar ->
        val (top, bottom) = ChartMath.barSpan(bar.value, domain, 0f, size.height)
        val height = bottom - top
        if (height <= 0f) return@forEachIndexed
        drawRoundRect(
            color = color,
            topLeft = Offset(index * slot + (slot - barWidth) / 2f, top),
            size = Size(barWidth, height),
            cornerRadius = CornerRadius(BAR_CORNER.toPx()),
        )
    }
}
