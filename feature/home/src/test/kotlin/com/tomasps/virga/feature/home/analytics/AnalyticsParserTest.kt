package com.tomasps.virga.feature.home.analytics

import com.tomasps.virga.core.ui.components.TimeRange
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class AnalyticsParserTest {

    private val now: Instant = Instant.parse("2026-11-11T12:00:00Z")
    private val window = analyticsWindow(TimeRange.TWENTY_FOUR_HOURS, now)
    private val zoneTag = "0f1e2d3c4b5a69788796a5b4c3d2e1f0"

    private fun json(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    @Test
    fun `workers rows are split at the start of the selected range`() {
        val data = json(
            """
            {"viewer":{"accounts":[{
              "hourly":[
                {"dimensions":{"datetime":"2026-11-09T13:00:00Z"},"sum":{"requests":5,"errors":2}},
                {"dimensions":{"datetime":"2026-11-10T13:00:00Z"},"sum":{"requests":10,"errors":1}},
                {"dimensions":{"datetime":"2026-11-11T11:00:00Z"},"sum":{"requests":4,"errors":0}}
              ],
              "currentCpu":[{"quantiles":{"cpuTimeP50":2000,"cpuTimeP99":18000}}],
              "previousCpu":[{"quantiles":{"cpuTimeP50":1000,"cpuTimeP99":9000}}],
              "byScript":[]
            }]}}
            """.trimIndent(),
        )

        val parsed = parseWorkersAnalytics(data, window)

        assertEquals(14L, parsed.currentRequests)
        assertEquals(1L, parsed.currentErrors)
        assertEquals(5L, parsed.previousRequests)
        assertEquals(2L, parsed.previousErrors)
    }

    @Test
    fun `cpu quantiles are converted from microseconds to milliseconds`() {
        val data = json(
            """{"viewer":{"accounts":[{"hourly":[],"currentCpu":[{"quantiles":{"cpuTimeP50":2000,"cpuTimeP99":18000}}],"previousCpu":[],"byScript":[]}]}}""",
        )

        val parsed = parseWorkersAnalytics(data, window)

        assertEquals(2.0, parsed.currentCpu.p50Ms!!, 0.0001)
        assertEquals(18.0, parsed.currentCpu.p99Ms!!, 0.0001)
    }

    @Test
    fun `script rows are summed per name and sorted by requests, highest first`() {
        val data = json(
            """
            {"viewer":{"accounts":[{"hourly":[],"currentCpu":[],"previousCpu":[],"byScript":[
              {"dimensions":{"scriptName":"alpha"},"sum":{"requests":3}},
              {"dimensions":{"scriptName":"beta"},"sum":{"requests":9}},
              {"dimensions":{"scriptName":"alpha"},"sum":{"requests":4}},
              {"dimensions":{},"sum":{"requests":100}}
            ]}]}}
            """.trimIndent(),
        )

        val parsed = parseWorkersAnalytics(data, window)

        assertEquals(listOf("beta" to 9L, "alpha" to 7L), parsed.byScript.map { it.scriptName to it.requests })
    }

    @Test
    fun `a missing account gives zero totals and no CPU figures`() {
        val parsed = parseWorkersAnalytics(json("""{"viewer":{"accounts":[]}}"""), window)

        assertEquals(0L, parsed.currentRequests)
        assertNull(parsed.currentCpu.p50Ms)
        assertEquals(emptyList<WorkerHourRow>(), parsed.hourly)
    }

    @Test
    fun `zone totals and per-zone requests count only the current range`() {
        val data = json(
            """
            {"viewer":{"accounts":[{"zones":[{"zoneTag":"$zoneTag","hourly":[
              {"dimensions":{"datetime":"2026-11-09T13:00:00Z"},"sum":{"requests":50,"cachedRequests":10}},
              {"dimensions":{"datetime":"2026-11-11T11:00:00Z"},"sum":{"requests":100,"cachedRequests":60}}
            ]}]}]}}
            """.trimIndent(),
        )

        val parsed = parseZonesAnalytics(listOf(data), window, zonesQueried = 1, zonesTotal = 3)

        assertEquals(100L, parsed.currentRequests)
        assertEquals(60L, parsed.currentCached)
        assertEquals(50L, parsed.previousRequests)
        assertEquals(mapOf(zoneTag to 100L), parsed.requestsByZone)
        assertEquals(1, parsed.zonesQueried)
        assertEquals(3, parsed.zonesTotal)
    }
}
