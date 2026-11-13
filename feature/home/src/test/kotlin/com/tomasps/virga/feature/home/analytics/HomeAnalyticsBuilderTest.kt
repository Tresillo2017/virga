package com.tomasps.virga.feature.home.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeAnalyticsBuilderTest {

    private val workers = WorkersAnalytics(
        currentRequests = 200,
        currentErrors = 4,
        previousRequests = 100,
        previousErrors = 2,
        currentCpu = CpuQuantiles(p50Ms = 2.0, p99Ms = 18.0),
        previousCpu = CpuQuantiles(p50Ms = 1.5, p99Ms = 15.0),
        hourly = listOf(WorkerHourRow(Instant0, requests = 200, errors = 4)),
        byScript = listOf(ScriptRequests("alpha", 150), ScriptRequests("beta", 50)),
    )

    private val zones = ZonesAnalytics(
        currentRequests = 1_000,
        currentCached = 600,
        previousRequests = 800,
        previousCached = 400,
        hourly = listOf(ZoneHourRow(Instant0, requests = 1_000, cached = 600)),
        requestsByZone = mapOf("zone-a" to 700L, "zone-b" to 300L),
        zonesQueried = 2,
        zonesTotal = 2,
    )

    @Test
    fun `a ratio with no requests is null, not zero percent`() {
        assertNull(ratioPercent(0, 0))
        assertEquals(25.0, ratioPercent(1, 4)!!, 0.0001)
    }

    @Test
    fun `the cache hit tile reports percentage points against the previous period`() {
        val home = buildHomeAnalytics(hasComparison = true, SourceOutcome.Loaded(workers), SourceOutcome.Loaded(zones))

        assertEquals(60.0, home.cacheHitRate.current!!, 0.0001)
        assertEquals(50.0, home.cacheHitRate.previous!!, 0.0001)
        assertEquals(10.0, home.cacheHitRate.pointChange()!!, 0.0001)
    }

    @Test
    fun `count tiles report a relative change against the previous period`() {
        val home = buildHomeAnalytics(hasComparison = true, SourceOutcome.Loaded(workers), SourceOutcome.Loaded(zones))

        assertEquals(100.0, home.invocations.relativeChange()!!, 0.0001)
        assertEquals(25.0, home.requests.relativeChange()!!, 0.0001)
    }

    @Test
    fun `a window without a comparison period leaves every previous value null`() {
        val home = buildHomeAnalytics(hasComparison = false, SourceOutcome.Loaded(workers), SourceOutcome.Loaded(zones))

        assertNull(home.requests.previous)
        assertNull(home.cacheHitRate.previous)
        assertNull(home.invocations.previous)
        assertNull(home.cpuP50Ms.previous)
        assertNull(home.requests.relativeChange())
    }

    @Test
    fun `a skipped or failed source leaves its tiles empty rather than zero`() {
        val home = buildHomeAnalytics(
            hasComparison = true,
            workers = SourceOutcome.Skipped,
            zones = SourceOutcome.Failed(AnalyticsIssue.MissingPermission(PERMISSION_ZONE_ANALYTICS)),
        )

        assertNull(home.invocations.current)
        assertNull(home.cpuP99Ms.current)
        assertNull(home.requests.current)
        assertEquals(AnalyticsIssue.MissingPermission(PERMISSION_ZONE_ANALYTICS), home.zonesIssue)
        assertNull(home.workersIssue)
    }

    @Test
    fun `every Worker the analytics returned keeps its requests, busiest first`() {
        val many = workers.copy(byScript = (1..7).map { ScriptRequests("w$it", it.toLong()) }.sortedByDescending { it.requests })

        val home = buildHomeAnalytics(hasComparison = true, SourceOutcome.Loaded(many), SourceOutcome.Skipped)

        assertEquals(7, home.requestsByWorker.size)
        assertEquals("w7", home.requestsByWorker.first().scriptName)
    }
}

private val Instant0: java.time.Instant = java.time.Instant.parse("2026-11-11T11:00:00Z")
