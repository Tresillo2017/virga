package com.tomasps.virga.feature.home.analytics

import com.tomasps.virga.core.ui.components.TimeRange
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PartialAnalyticsTest {

    private val now: Instant = Instant.parse("2026-11-11T12:00:00Z")
    private val window = analyticsWindow(TimeRange.SEVEN_DAYS, now)

    @Test
    fun `a full hourly response is flagged as truncated, because totals may be short`() {
        val rows = (0 until MAX_HOURLY_ROWS).joinToString(",") {
            """{"dimensions":{"datetime":"2026-11-11T11:00:00Z"},"sum":{"requests":1,"errors":0}}"""
        }

        val parsed = parseWorkersAnalytics(json("""{"viewer":{"accounts":[{"hourly":[$rows]}]}}"""), window)

        assertTrue(parsed.totalsTruncated)
    }

    @Test
    fun `a short hourly response is not truncated`() {
        val parsed = parseWorkersAnalytics(json("""{"viewer":{"accounts":[{"hourly":[]}]}}"""), window)

        assertFalse(parsed.totalsTruncated)
        assertFalse(parsed.topTruncated)
    }

    @Test
    fun `a full per-script response marks the top list as approximate`() {
        val scripts = (0 until TOP_SCRIPT_LIMIT).joinToString(",") {
            """{"dimensions":{"scriptName":"worker-$it"},"sum":{"requests":1}}"""
        }

        val parsed = parseWorkersAnalytics(json("""{"viewer":{"accounts":[{"byScript":[$scripts]}]}}"""), window)

        assertTrue(parsed.topTruncated)
        assertFalse(parsed.totalsTruncated)
    }

    @Test
    fun `a failed zone chunk marks the zone analytics as partial`() {
        val parsed = parseZonesAnalytics(emptyList(), window, zonesQueried = 1, zonesTotal = 2, chunkFailures = 1)

        assertTrue(parsed.partial)
    }

    @Test
    fun `complete zone analytics are not partial`() {
        val parsed = parseZonesAnalytics(emptyList(), window, zonesQueried = 1, zonesTotal = 1)

        assertFalse(parsed.partial)
    }

    @Test
    fun `a partial zone source hides its deltas and shows the partial issue`() {
        val home = buildHomeAnalytics(
            hasComparison = true,
            workers = SourceOutcome.Loaded(workers(totalsTruncated = false)),
            zones = SourceOutcome.Loaded(zones(partial = true)),
        )

        assertEquals(AnalyticsIssue.Partial, home.zonesIssue)
        assertNull(home.requests.previous)
        assertNull(home.requests.relativeChange())
        assertNull(home.cacheHitRate.previous)
        assertEquals(1_000.0, home.requests.current!!, 0.0001)
    }

    @Test
    fun `a partial worker source keeps zone deltas and hides only the worker deltas`() {
        val home = buildHomeAnalytics(
            hasComparison = true,
            workers = SourceOutcome.Loaded(workers(totalsTruncated = true)),
            zones = SourceOutcome.Loaded(zones(partial = false)),
        )

        assertEquals(AnalyticsIssue.Partial, home.workersIssue)
        assertNull(home.invocations.previous)
        assertNull(home.errors.previous)
        assertEquals(800.0, home.requests.previous!!, 0.0001)
    }

    @Test
    fun `the top workers list carries the approximate flag from the parser`() {
        val home = buildHomeAnalytics(
            hasComparison = true,
            workers = SourceOutcome.Loaded(workers(totalsTruncated = false).copy(topTruncated = true)),
            zones = SourceOutcome.Skipped,
        )

        assertTrue(home.workerCountsApproximate)
    }

    private fun workers(totalsTruncated: Boolean) = WorkersAnalytics(
        currentRequests = 200,
        currentErrors = 2,
        previousRequests = 100,
        previousErrors = 1,
        currentCpu = CpuQuantiles(2.0, 18.0),
        previousCpu = CpuQuantiles(2.0, 18.0),
        hourly = emptyList(),
        byScript = emptyList(),
        totalsTruncated = totalsTruncated,
    )

    private fun zones(partial: Boolean) = ZonesAnalytics(
        currentRequests = 1_000,
        currentCached = 600,
        previousRequests = 800,
        previousCached = 400,
        hourly = emptyList(),
        requestsByZone = emptyMap(),
        zonesQueried = 1,
        zonesTotal = 1,
        partial = partial,
    )

    private fun json(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject
}
