package com.tomasps.virga.feature.home.data

import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.core.ui.components.TimeRange
import com.tomasps.virga.feature.home.analytics.analyticsWindow
import com.tomasps.virga.feature.home.search.SearchSource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CloudflareHomeRepositoryTest {

    private val window = analyticsWindow(TimeRange.TWENTY_FOUR_HOURS, Instant.parse("2026-11-11T12:00:00Z"))
    private val emptyZonesReply = Json.parseToJsonElement("""{"viewer":{"accounts":[{"zones":[]}]}}""")

    @Test
    fun `a list is read once per account and then served from the cache`() = runBlocking {
        // Arrange
        var calls = 0
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/workers/scripts") {
                calls++
                DemoReply.ok(buildJsonArray { add(buildJsonObject { put("id", "orbit-api") }) })
            }
        }
        val repository = CloudflareHomeRepository(api)

        // Act
        repository.list("acc-1", SearchSource.WORKERS)
        val second = repository.list("acc-1", SearchSource.WORKERS)

        // Assert
        assertEquals(1, calls)
        assertEquals("orbit-api", (second as ApiResult.Success).value.single().title)
    }

    @Test
    fun `a forced refresh reads the list again`() = runBlocking {
        var calls = 0
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/workers/scripts") {
                calls++
                DemoReply.ok(buildJsonArray { })
            }
        }
        val repository = CloudflareHomeRepository(api)

        repository.list("acc-1", SearchSource.WORKERS)
        repository.list("acc-1", SearchSource.WORKERS, forceRefresh = true)

        assertEquals(2, calls)
    }

    @Test
    fun `the cache is kept per account`() = runBlocking {
        var calls = 0
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/workers/scripts") {
                calls++
                DemoReply.ok(buildJsonArray { })
            }
        }
        val repository = CloudflareHomeRepository(api)

        repository.list("acc-1", SearchSource.WORKERS)
        repository.list("acc-2", SearchSource.WORKERS)

        assertEquals(2, calls)
    }

    @Test
    fun `zones are requested for the account with the account filter`() = runBlocking {
        val api = mockCloudflareApi {
            get("/zones") { request ->
                assertEquals("acc-1", request.query["account.id"])
                DemoReply.ok(buildJsonArray { })
            }
        }

        val result = CloudflareHomeRepository(api).list("acc-1", SearchSource.ZONES)

        assertTrue(result is ApiResult.Success)
    }

    @Test
    fun `a forbidden list surfaces as a Forbidden failure so search can name the permission`() = runBlocking {
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/pages/projects") { DemoReply.failure(io.ktor.http.HttpStatusCode.Forbidden, 10000, "denied") }
        }

        val result = CloudflareHomeRepository(api).list("acc-1", SearchSource.PAGES)

        assertTrue((result as ApiResult.Failure).error is ApiError.Forbidden)
    }

    @Test
    fun `zone analytics query at most ten zones at a time and skip malformed zone ids`() = runBlocking {
        val posted = mutableListOf<String>()
        val api = mockCloudflareApi {
            post("/graphql") { request ->
                posted += request.bodyText.orEmpty()
                DemoReply.graphQl(emptyZonesReply)
            }
        }
        val zoneIds = (1..12).map { "%032x".format(it) } + "not-a-zone"

        val result = CloudflareHomeRepository(api).zoneAnalytics("acc-1", zoneIds, window)

        assertEquals(2, posted.size)
        assertTrue(posted.none { it.contains("not-a-zone") })
        assertEquals(12, (result as ApiResult.Success).value.zonesQueried)
        assertEquals(13, result.value.zonesTotal)
    }

    @Test
    fun `worker analytics report a GraphQL error as a failure`() = runBlocking {
        val api = mockCloudflareApi {
            post("/graphql") { DemoReply.graphQlErrors("boom") }
        }

        val result = CloudflareHomeRepository(api).workerAnalytics("acc-1", window)

        assertTrue(result is ApiResult.Failure)
    }
}
