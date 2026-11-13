package com.tomasps.virga.feature.home.demo

import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.core.ui.components.TimeRange
import com.tomasps.virga.feature.home.analytics.analyticsWindow
import com.tomasps.virga.feature.home.data.CloudflareHomeRepository
import com.tomasps.virga.feature.home.search.SearchSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class HomeDemoRoutesTest {

    private val api = mockCloudflareApi { HomeDemoRoutes().contribute(this) }
    private val repository = CloudflareHomeRepository(api)
    private val window = analyticsWindow(TimeRange.TWENTY_FOUR_HOURS, Instant.parse("2026-11-11T12:00:00Z"))

    @Test
    fun `demo analytics answer the Home queries with per-Worker and per-zone rows`() = runBlocking {
        val workers = repository.workerAnalytics("demo-account-personal", window) as ApiResult.Success
        val zoneIds = DEMO_ZONES.map { it.id }
        val zones = repository.zoneAnalytics("demo-account-personal", zoneIds, window) as ApiResult.Success

        assertEquals(DEMO_SCRIPTS.map { it.first }, workers.value.byScript.map { it.scriptName })
        assertTrue(workers.value.currentRequests > 0)
        assertEquals(zoneIds.toSet(), zones.value.requestsByZone.keys)
    }
}
