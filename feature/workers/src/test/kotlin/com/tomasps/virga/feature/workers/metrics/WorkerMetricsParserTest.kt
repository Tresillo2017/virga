package com.tomasps.virga.feature.workers.metrics

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkerMetricsParserTest {

    @Test
    fun `rows sharing a time bucket are summed and status codes are totalled`() {
        val metrics = WorkerMetricsParser.parse(
            response(
                row(time = "2026-11-10T10:00:00Z", status = "200", requests = 10, errors = 1, subrequests = 4),
                row(time = "2026-11-10T10:00:00Z", status = "500", requests = 2, errors = 2, subrequests = 1),
                row(time = "2026-11-10T11:00:00Z", status = "200", requests = 5, errors = 0, subrequests = 0),
            ),
        )

        assertEquals(2, metrics.buckets.size)
        assertEquals(12.0, metrics.buckets.first().requests, 0.0)
        assertEquals(3.0, metrics.buckets.first().errors, 0.0)
        assertEquals(17.0, metrics.totalRequests, 0.0)
        assertEquals(listOf("200", "500"), metrics.statuses.map { it.status })
        assertEquals(15.0, metrics.statuses.first { it.status == "200" }.requests, 0.0)
    }

    @Test
    fun `cpu quantiles keep the highest value across the rows of one bucket`() {
        val metrics = WorkerMetricsParser.parse(
            response(
                row(time = "2026-11-10T10:00:00Z", status = "200", requests = 10, cpuP50 = 800.0, cpuP99 = 9_000.0),
                row(time = "2026-11-10T10:00:00Z", status = "500", requests = 2, cpuP50 = 1_200.0, cpuP99 = 4_000.0),
            ),
        )

        assertEquals(1_200.0, metrics.buckets.single().cpuP50Us!!, 0.0)
        assertEquals(9_000.0, metrics.buckets.single().cpuP99Us!!, 0.0)
    }

    @Test
    fun `buckets come back in time order whatever order the API sends`() {
        val metrics = WorkerMetricsParser.parse(
            response(
                row(time = "2026-11-10T12:00:00Z", status = "200", requests = 1),
                row(time = "2026-11-10T09:00:00Z", status = "200", requests = 1),
            ),
        )

        assertEquals(listOf("2026-11-10T09:00:00Z", "2026-11-10T12:00:00Z"), metrics.buckets.map { it.time })
    }

    @Test
    fun `rows without a time are skipped instead of failing the whole response`() {
        val metrics = WorkerMetricsParser.parse(
            response(
                """{"sum":{"requests":3},"dimensions":{"status":"200"}}""",
                row(time = "2026-11-10T09:00:00Z", status = "200", requests = 1),
            ),
        )

        assertEquals(1, metrics.buckets.size)
        assertEquals(1.0, metrics.totalRequests, 0.0)
    }

    @Test
    fun `a response without a viewer or account is empty, not a crash`() {
        val metrics = WorkerMetricsParser.parse(JsonObject(emptyMap()))

        assertEquals(0, metrics.buckets.size)
        assertEquals(0.0, metrics.totalRequests, 0.0)
    }

    @Test
    fun `missing quantiles stay null on the bucket`() {
        val metrics = WorkerMetricsParser.parse(response(row(time = "2026-11-10T09:00:00Z", status = "200", requests = 1)))

        assertNull(metrics.buckets.single().cpuP99Us)
    }

    private fun row(
        time: String,
        status: String,
        requests: Int,
        errors: Int = 0,
        subrequests: Int = 0,
        cpuP50: Double? = null,
        cpuP99: Double? = null,
    ): String {
        val quantiles = if (cpuP50 == null && cpuP99 == null) "" else
            ""","quantiles":{"cpuTimeP50":${cpuP50 ?: 0.0},"cpuTimeP99":${cpuP99 ?: 0.0}}"""
        return """{"sum":{"requests":$requests,"errors":$errors,"subrequests":$subrequests}$quantiles,""" +
            """"dimensions":{"datetime":"$time","scriptName":"edge-api","status":"$status"}}"""
    }

    private fun response(vararg rows: String): JsonObject = response(rows.toList())

    private fun response(rows: List<String>): JsonObject = Json.parseToJsonElement(
        """{"viewer":{"accounts":[{"workersInvocationsAdaptive":[${rows.joinToString(",")}]}]}}""",
    ).let { it as JsonObject }
}
