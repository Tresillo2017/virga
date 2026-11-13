package com.tomasps.virga.feature.home.data

import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.core.ui.components.TimeRange
import com.tomasps.virga.feature.home.analytics.analyticsWindow
import com.tomasps.virga.feature.home.analytics.ZONES_PER_QUERY
import com.tomasps.virga.feature.home.search.SearchSource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CloudflareHomeRepositoryPagingTest {

    private val window = analyticsWindow(TimeRange.TWENTY_FOUR_HOURS, Instant.parse("2026-11-11T12:00:00Z"))
    private val emptyZonesReply: JsonElement = Json.parseToJsonElement("""{"viewer":{"accounts":[{"zones":[]}]}}""")

    @Test
    fun `zones are requested 50 per page and every page is read`() = runBlocking {
        // Arrange: two pages of one zone each, reporting per_page 1 and total_count 2
        val perPageSent = mutableListOf<String?>()
        val api = mockCloudflareApi {
            get("/zones") { request ->
                perPageSent += request.query["per_page"]
                val page = request.query["page"]?.toInt() ?: 1
                DemoReply.ok(
                    buildJsonArray { add(zone("%032d".format(page))) },
                    resultInfo = buildJsonObject {
                        put("page", page)
                        put("per_page", 1)
                        put("count", 1)
                        put("total_count", 2)
                    },
                )
            }
        }

        // Act
        val result = CloudflareHomeRepository(api).list("acc-1", SearchSource.ZONES) as ApiResult.Success

        // Assert
        assertEquals(listOf("50", "50"), perPageSent)
        assertEquals(listOf("%032d".format(1), "%032d".format(2)), result.value.map { it.id })
    }

    @Test
    fun `R2 buckets are read from the wrapper object across cursor pages`() = runBlocking {
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/r2/buckets") { request ->
                val firstPage = request.query["cursor"] == null
                val bucket = if (firstPage) "photo-archive" else "release-artifacts"
                // The last page carries no cursor, so the walk ends after two requests.
                DemoReply.ok(
                    buildJsonObject { put("buckets", buildJsonArray { add(buildJsonObject { put("name", bucket) }) }) },
                    resultInfo = if (firstPage) buildJsonObject { put("cursor", "next-page") } else null,
                )
            }
        }

        val result = CloudflareHomeRepository(api).list("acc-1", SearchSource.R2) as ApiResult.Success

        assertEquals(listOf("photo-archive", "release-artifacts"), result.value.map { it.title })
    }

    @Test
    fun `a list that never ends fails visibly instead of returning a silent prefix`() = runBlocking {
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/pages/projects") { request ->
                DemoReply.ok(
                    buildJsonArray { add(buildJsonObject { put("name", "project-${request.query["page"]}") }) },
                    resultInfo = buildJsonObject {
                        put("page", request.query["page"]?.toInt() ?: 1)
                        put("per_page", 1)
                        put("count", 1)
                        put("total_count", Int.MAX_VALUE)
                    },
                )
            }
        }

        val result = CloudflareHomeRepository(api).list("acc-1", SearchSource.PAGES)

        assertTrue(result is ApiResult.Failure)
    }

    @Test
    fun `a zone chunk that fails marks the zone analytics as partial`() = runBlocking {
        // Arrange: 11 zones make two GraphQL chunks, and the second one fails
        val zoneIds = (1..ZONES_PER_QUERY + 1).map { "%032d".format(it) }
        var graphQlCalls = 0
        val api = mockCloudflareApi {
            post("/graphql") {
                graphQlCalls++
                if (graphQlCalls == 1) DemoReply.graphQl(emptyZonesReply) else DemoReply.graphQlErrors("upstream failed")
            }
        }

        // Act
        val result = CloudflareHomeRepository(api).zoneAnalytics("acc-1", zoneIds, window)

        // Assert
        val analytics = (result as ApiResult.Success).value
        assertTrue(analytics.partial)
        assertEquals(ZONES_PER_QUERY, analytics.zonesQueried)
        assertEquals(ZONES_PER_QUERY + 1, analytics.zonesTotal)
    }

    private fun zone(id: String) = buildJsonObject {
        put("id", id)
        put("name", "zone-$id.example")
    }
}
