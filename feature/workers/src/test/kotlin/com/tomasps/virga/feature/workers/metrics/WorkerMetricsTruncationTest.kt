package com.tomasps.virga.feature.workers.metrics

import com.tomasps.virga.core.ui.chart.ChartPoint
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkerMetricsTruncationTest {

    private fun rootWithRows(count: Int): JsonObject = buildJsonObject {
        put("viewer", buildJsonObject {
            put("accounts", buildJsonArray {
                add(buildJsonObject {
                    put("workersInvocationsAdaptive", buildJsonArray {
                        repeat(count) { index ->
                            add(buildJsonObject {
                                put("sum", buildJsonObject { put("requests", 1); put("errors", 0); put("subrequests", 0) })
                                put("dimensions", buildJsonObject {
                                    put("datetime", "2026-11-10T00:0$index:00Z")
                                    put("scriptName", "edge-api")
                                    put("status", "200")
                                })
                            })
                        }
                    })
                })
            })
        })
    }

    @Test
    fun `a result that fills the row limit is flagged as truncated`() {
        val metrics = WorkerMetricsParser.parse(rootWithRows(3), rowLimit = 3)

        assertTrue(metrics.isTruncated)
    }

    @Test
    fun `a result below the row limit is complete`() {
        val metrics = WorkerMetricsParser.parse(rootWithRows(2), rowLimit = 3)

        assertFalse(metrics.isTruncated)
    }

    @Test
    fun `without a limit the result is never flagged`() {
        assertFalse(WorkerMetricsParser.parse(rootWithRows(5)).isTruncated)
    }

    @Test
    fun `chart points skip buckets with no value instead of plotting zero`() {
        val buckets = listOf(
            MetricsBucket("t1", 1.0, 0.0, 0.0, cpuP50Us = 120.0, cpuP99Us = null),
            MetricsBucket("t2", 1.0, 0.0, 0.0, cpuP50Us = null, cpuP99Us = 480.0),
        )

        val p50 = chartPoints(buckets) { it.cpuP50Us }

        assertEquals(listOf(ChartPoint("t1", 120.0)), p50)
    }
}
