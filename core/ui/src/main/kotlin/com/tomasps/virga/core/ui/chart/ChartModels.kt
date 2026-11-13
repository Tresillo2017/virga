package com.tomasps.virga.core.ui.chart

import androidx.compose.runtime.Immutable

/** One labelled value on a line or bar chart. [label] is the x-axis text and is read by screen readers. */
@Immutable
data class ChartPoint(
    val label: String,
    val value: Double,
)

/** One named line of a [MultiLineChart]. Points line up by position, so every series shares the x-axis labels. */
@Immutable
data class LineSeries(
    val name: String,
    val points: List<ChartPoint>,
)

/** One column of a stacked bar chart. [segments] line up with the chart's segment names, bottom first. */
@Immutable
data class StackedColumn(
    val label: String,
    val segments: List<Double>,
)
