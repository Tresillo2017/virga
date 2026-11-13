package com.tomasps.virga.core.ui.chart

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Visible value range of a chart axis. [max] is at the top of the plot, [min] at the bottom. */
data class ChartDomain(val min: Double, val max: Double) {
    val span: Double get() = max - min
}

/** Pure chart geometry. Pixel positions are measured downward from the top edge, as Canvas expects. */
object ChartMath {
    private const val RELATIVE_PADDING = 0.1
    private const val MIN_PADDING = 1.0
    private const val EMPTY_MIN = 0.0
    private const val EMPTY_MAX = 1.0

    fun finiteValues(values: List<Double>): List<Double> = values.filter { it.isFinite() }

    /**
     * Axis range for [values]. NaN and infinite values are ignored. [includeZero] extends the range to
     * contain zero, which bars need so their baseline is honest. A flat series gets padding on both sides.
     */
    fun domain(values: List<Double>, includeZero: Boolean): ChartDomain {
        val finite = finiteValues(values)
        if (finite.isEmpty()) return ChartDomain(EMPTY_MIN, EMPTY_MAX)
        var low = finite.min()
        var high = finite.max()
        if (includeZero) {
            low = min(low, 0.0)
            high = max(high, 0.0)
        }
        if (high > low) return ChartDomain(low, high)
        val padding = max(abs(low) * RELATIVE_PADDING, MIN_PADDING)
        return ChartDomain(low - padding, high + padding)
    }

    /** Position of [value] within [domain] as a fraction: 0 at the bottom, 1 at the top. */
    fun fraction(value: Double, domain: ChartDomain): Double {
        if (domain.span <= 0.0) return 0.5
        return (value - domain.min) / domain.span
    }

    /** Pixel y for [value], with [top] and [bottom] the plot edges in pixels (top < bottom). */
    fun yPixel(value: Double, domain: ChartDomain, top: Float, bottom: Float): Float =
        (bottom - fraction(value, domain) * (bottom - top)).toFloat()

    /**
     * Evenly spaced x positions for [count] points across [left]..[right].
     * One point sits in the middle; no points gives an empty list.
     */
    fun xPositions(count: Int, left: Float, right: Float): List<Float> = when {
        count <= 0 -> emptyList()
        count == 1 -> listOf((left + right) / 2f)
        else -> List(count) { index -> left + (right - left) * index / (count - 1) }
    }

    /**
     * Top and bottom pixel of a bar for [value], measured from the zero baseline.
     * Negative values hang below the baseline. The result is clamped to the plot.
     */
    fun barSpan(value: Double, domain: ChartDomain, top: Float, bottom: Float): Pair<Float, Float> {
        val valuePixel = yPixel(value, domain, top, bottom).coerceIn(top, bottom)
        val baselinePixel = yPixel(0.0, domain, top, bottom).coerceIn(top, bottom)
        return min(valuePixel, baselinePixel) to max(valuePixel, baselinePixel)
    }

    /**
     * Stacked segment spans for one column, bottom segment first. Segments are assumed non-negative;
     * use [normalizeSegments] first.
     */
    fun stackSpans(
        segments: List<Double>,
        domain: ChartDomain,
        top: Float,
        bottom: Float,
    ): List<Pair<Float, Float>> {
        var running = 0.0
        return segments.map { segment ->
            val start = running
            running += segment
            val startPixel = yPixel(start, domain, top, bottom).coerceIn(top, bottom)
            val endPixel = yPixel(running, domain, top, bottom).coerceIn(top, bottom)
            min(startPixel, endPixel) to max(startPixel, endPixel)
        }
    }

    /**
     * Makes one column's segments match the legend: exactly [segmentCount] values, missing or extra
     * entries dropped or zero-filled, and NaN, infinite or negative values treated as zero.
     */
    fun normalizeSegments(segments: List<Double>, segmentCount: Int): List<Double> =
        List(max(segmentCount, 0)) { index ->
            val value = segments.getOrNull(index)
            if (value != null && value.isFinite() && value > 0.0) value else 0.0
        }

    /** Column totals after normalisation, used as the stacked chart's value range. */
    fun stackedTotals(columns: List<List<Double>>): List<Double> =
        columns.map { column -> column.sum() }
}
