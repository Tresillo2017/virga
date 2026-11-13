package com.tomasps.virga.feature.workers.demo

import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.feature.workers.metrics.WorkerMetrics
import com.tomasps.virga.feature.workers.metrics.WorkerMetricsRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

private const val ACCOUNT = "demo-account-personal"
private val START: Instant = Instant.parse("2026-11-10T00:00:00Z")

class WorkersDemoMetricsTest {

    private fun load(script: String, end: Instant): WorkerMetrics = runBlocking {
        val api = mockCloudflareApi { WorkersDemoRoutes().contribute(this) }
        (WorkerMetricsRepository(api).load(ACCOUNT, script, START, end) as ApiResult.Success).value
    }

    @Test
    fun `a day of hourly metrics has one bucket per hour and both 200 and 500 statuses`() {
        val metrics = load(DEMO_SCRIPT_EDGE_API, START.plus(24, ChronoUnit.HOURS))

        assertEquals(24, metrics.buckets.size)
        assertEquals(listOf("200", "500"), metrics.statuses.map { it.status })
        assertFalse(metrics.isTruncated)
    }

    @Test
    fun `errors are a small share of requests and the server error count matches the 500 rows`() {
        val metrics = load(DEMO_SCRIPT_EDGE_API, START.plus(24, ChronoUnit.HOURS))

        assertTrue(metrics.totalErrors > 0)
        assertTrue(metrics.totalErrors < metrics.totalRequests * 0.05)
    }

    @Test
    fun `the window is capped at a month of hours`() {
        val metrics = load(DEMO_SCRIPT_RESIZER, START.plus(5_000, ChronoUnit.HOURS))

        assertEquals(744, metrics.buckets.size)
    }

    @Test
    fun `each Worker gets its own numbers from the same window`() {
        val edge = load(DEMO_SCRIPT_EDGE_API, START.plus(12, ChronoUnit.HOURS))
        val resizer = load(DEMO_SCRIPT_RESIZER, START.plus(12, ChronoUnit.HOURS))

        assertTrue(edge.totalRequests != resizer.totalRequests)
    }
}
