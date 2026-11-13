package com.tomasps.virga.feature.r2.api

import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class R2MetricsRepositoryTest {

    @Test
    fun `operations are summed per action and storage keeps its time order`() = runBlocking {
        // Arrange
        val api = mockCloudflareApi {
            post("/graphql") {
                DemoReply.graphQl(
                    buildJsonObject {
                        put("viewer", buildJsonObject {
                            put("accounts", buildJsonArray {
                                add(buildJsonObject {
                                    put("r2OperationsAdaptiveGroups", buildJsonArray {
                                        add(group("GetObject", 30))
                                        add(group("GetObject", 12))
                                        add(group("PutObject", 5))
                                    })
                                    put("r2StorageAdaptiveGroups", buildJsonArray {
                                        add(storage("2026-11-01T00:00:00Z", 3, 3_000))
                                        add(storage("2026-11-02T00:00:00Z", 4, 4_000))
                                    })
                                })
                            })
                        })
                    },
                )
            }
        }

        // Act
        val result = R2MetricsRepository(api).bucketMetrics("acc-1", "site", Instant.EPOCH, Instant.EPOCH.plusSeconds(60))

        // Assert
        val metrics = (result as ApiResult.Success).value
        assertEquals(listOf("GetObject" to 42L, "PutObject" to 5L), metrics.operations.map { it.action to it.requests })
        assertEquals(listOf(3L, 4L), metrics.storage.map { it.objectCount })
        assertEquals("2026-11-02T00:00:00Z", metrics.storage.last().datetime)
    }

    @Test
    fun `the query asks only for the operations and storage datasets`() = runBlocking {
        // Arrange
        var sentQuery = ""
        val api = mockCloudflareApi {
            post("/graphql") { request ->
                sentQuery = request.bodyText.orEmpty()
                DemoReply.graphQl(buildJsonObject {})
            }
        }

        // Act
        R2MetricsRepository(api).bucketMetrics("acc-1", "site", Instant.EPOCH, Instant.EPOCH.plusSeconds(60))

        // Assert
        assertTrue(sentQuery.contains("r2OperationsAdaptiveGroups"))
        assertTrue(sentQuery.contains("r2StorageAdaptiveGroups"))
        assertFalse(sentQuery.contains("Bandwidth"))
    }

    private fun group(action: String, requests: Long) = buildJsonObject {
        put("sum", buildJsonObject { put("requests", requests) })
        put("dimensions", buildJsonObject { put("actionType", action) })
    }

    private fun storage(datetime: String, objects: Long, bytes: Long) = buildJsonObject {
        put("max", buildJsonObject { put("objectCount", objects); put("payloadSize", bytes) })
        put("dimensions", buildJsonObject { put("datetime", datetime) })
    }
}
