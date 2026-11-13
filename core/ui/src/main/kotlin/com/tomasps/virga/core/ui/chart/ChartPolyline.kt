package com.tomasps.virga.core.ui.chart

/** Pixel path for a series, shared by the line chart and the sparkline. Pure, so it is testable. */
object ChartPolyline {
    /**
     * (x, y) pixel pairs for the finite [values], left to right, inside the box
     * ([left], [top]) to ([right], [bottom]). NaN and infinite values are skipped.
     */
    fun build(
        values: List<Double>,
        domain: ChartDomain,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ): List<Pair<Float, Float>> {
        val finite = ChartMath.finiteValues(values)
        val xs = ChartMath.xPositions(finite.size, left, right)
        return finite.mapIndexed { index, value ->
            xs[index] to ChartMath.yPixel(value, domain, top, bottom)
        }
    }
}
