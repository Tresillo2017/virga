package com.tomasps.virga.feature.workers.metrics

import com.tomasps.virga.core.ui.chart.ChartPoint
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.util.Locale

class BucketLabelsTest {

    @Test
    fun `a bucket time reads as a short date and clock time, never as raw ISO`() {
        val label = formatBucketLabel("2026-11-11T09:00:00Z", zone = ZoneId.of("UTC"), locale = Locale.US)

        assertEquals("Nov 11, 09:00", label)
    }

    @Test
    fun `a value that does not parse is kept as it is`() {
        assertEquals("not-a-time", formatBucketLabel("not-a-time", zone = ZoneId.of("UTC"), locale = Locale.US))
    }

    @Test
    fun `chart points carry the readable label and drop buckets with no value`() {
        val buckets = listOf(
            MetricsBucket("2026-11-11T09:00:00Z", 10.0, 0.0, 0.0, cpuP50Us = null, cpuP99Us = null),
            MetricsBucket("2026-11-11T10:00:00Z", 12.0, 1.0, 0.0, cpuP50Us = 2_000.0, cpuP99Us = 18_000.0),
        )

        val points = chartPoints(buckets) { it.cpuP50Us }

        assertEquals(listOf(ChartPoint(formatBucketLabel("2026-11-11T10:00:00Z"), 2_000.0)), points)
    }
}
