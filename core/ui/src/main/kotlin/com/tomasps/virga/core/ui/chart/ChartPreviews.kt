package com.tomasps.virga.core.ui.chart

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.ui.theme.VirgaTheme

private val SAMPLE_POINTS = listOf(
    ChartPoint("00:00", 120.0), ChartPoint("04:00", 180.0), ChartPoint("08:00", 150.0),
    ChartPoint("12:00", 420.0), ChartPoint("16:00", 380.0), ChartPoint("20:00", 510.0),
)
private val SAMPLE_BARS = listOf(
    ChartPoint("Mon", 12.0), ChartPoint("Tue", -4.0), ChartPoint("Wed", 25.0), ChartPoint("Thu", 18.0),
)
private val SAMPLE_COLUMNS = listOf(
    StackedColumn("Mon", listOf(10.0, 4.0, 2.0)),
    StackedColumn("Tue", listOf(6.0, 8.0, 1.0)),
    StackedColumn("Wed", listOf(12.0, 3.0, 5.0)),
)
private val SEGMENTS = listOf("2xx", "3xx", "5xx")

@Preview(name = "Line, dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun LineChartPreview() {
    VirgaTheme(darkTheme = true) {
        LineChart(points = SAMPLE_POINTS, title = "Requests", modifier = Modifier.padding(16.dp))
    }
}

@Preview(name = "Line, light", showBackground = true)
@Composable
private fun LineChartLightPreview() {
    VirgaTheme(darkTheme = false) {
        LineChart(points = SAMPLE_POINTS, title = "Requests", modifier = Modifier.padding(16.dp))
    }
}

@Preview(name = "Line, empty", showBackground = true)
@Composable
private fun LineChartEmptyPreview() {
    VirgaTheme(darkTheme = true) {
        LineChart(points = emptyList(), title = "Requests", modifier = Modifier.padding(16.dp))
    }
}

@Preview(name = "Line, single point", showBackground = true)
@Composable
private fun LineChartSinglePreview() {
    VirgaTheme(darkTheme = true) {
        LineChart(points = listOf(ChartPoint("now", 42.0)), title = "Requests", modifier = Modifier.padding(16.dp))
    }
}

private val SAMPLE_SERIES = listOf(
    LineSeries("2xx", listOf(ChartPoint("00:00", 120.0), ChartPoint("04:00", 180.0), ChartPoint("08:00", 260.0), ChartPoint("12:00", 420.0))),
    LineSeries("5xx", listOf(ChartPoint("00:00", 4.0), ChartPoint("04:00", 9.0), ChartPoint("08:00", 2.0), ChartPoint("12:00", 14.0))),
    LineSeries("4xx", listOf(ChartPoint("00:00", 40.0), ChartPoint("04:00", 35.0), ChartPoint("08:00", 51.0), ChartPoint("12:00", 48.0))),
)

@Preview(name = "Multi-line with legend, dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun MultiLineChartPreview() {
    VirgaTheme(darkTheme = true) {
        MultiLineChart(series = SAMPLE_SERIES, title = "Status codes", modifier = Modifier.padding(16.dp))
    }
}

@Preview(name = "Multi-line, empty", showBackground = true)
@Composable
private fun MultiLineChartEmptyPreview() {
    VirgaTheme(darkTheme = true) {
        MultiLineChart(series = emptyList(), title = "Status codes", modifier = Modifier.padding(16.dp))
    }
}

@Preview(name = "Bars, with a negative", showBackground = true)
@Composable
private fun BarChartPreview() {
    VirgaTheme(darkTheme = true) {
        BarChart(bars = SAMPLE_BARS, title = "Errors", modifier = Modifier.padding(16.dp))
    }
}

@Preview(name = "Stacked", showBackground = true)
@Composable
private fun StackedBarChartPreview() {
    VirgaTheme(darkTheme = true) {
        StackedBarChart(
            columns = SAMPLE_COLUMNS,
            segmentNames = SEGMENTS,
            title = "Status codes",
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(name = "Stacked, empty", showBackground = true)
@Composable
private fun StackedBarChartEmptyPreview() {
    VirgaTheme(darkTheme = true) {
        Column(Modifier.padding(16.dp)) {
            StackedBarChart(columns = emptyList(), segmentNames = SEGMENTS, title = "Status codes")
        }
    }
}

@Preview(name = "Sparkline", showBackground = true)
@Composable
private fun SparklinePreview() {
    VirgaTheme(darkTheme = true) {
        Sparkline(values = listOf(3.0, 5.0, 4.0, 7.0, 6.0, 9.0), modifier = Modifier.padding(16.dp))
    }
}
