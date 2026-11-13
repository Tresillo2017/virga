package com.tomasps.virga.core.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.ui.theme.LocalVirgaSemanticColors

private val SPARKLINE_HEIGHT = 40.dp
private val SPARKLINE_WIDTH = 2.dp
private val SPARKLINE_DOT = 3.dp

/**
 * Small trend line with no axes. Decorative: it is removed from the accessibility tree, so the
 * enclosing card's description carries the information. Empty data draws nothing.
 */
@Composable
fun Sparkline(
    values: List<Double>,
    modifier: Modifier = Modifier,
    color: Color = LocalVirgaSemanticColors.current.chartSeries.first(),
) {
    val finite = remember(values) { ChartMath.finiteValues(values) }
    val domain = remember(finite) { ChartMath.domain(finite, includeZero = false) }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(SPARKLINE_HEIGHT)
            .clearAndSetSemantics {},
    ) {
        if (finite.isEmpty()) return@Canvas
        val inset = SPARKLINE_WIDTH.toPx()
        val coordinates = ChartPolyline.build(
            values = finite,
            domain = domain,
            left = inset,
            top = inset,
            right = size.width - inset,
            bottom = size.height - inset,
        )
        if (coordinates.size == 1) {
            val (x, y) = coordinates.single()
            drawCircle(color = color, radius = SPARKLINE_DOT.toPx(), center = Offset(x, y))
        } else {
            drawPath(
                path = linePath(coordinates),
                color = color,
                style = Stroke(width = SPARKLINE_WIDTH.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}
