package com.tomasps.virga.feature.d1.data

import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

private val START: LocalDate = LocalDate.of(2026, 11, 4)
private val END: LocalDate = LocalDate.of(2026, 11, 10)

class MetricsRepositoryTest {

    @Test
    fun `daily series is parsed in date order with query and row totals`() = runBlocking<Unit> {
        // Arrange
        val api = mockCloudflareApi {
            post("/graphql") { request ->
                assertTrue(request.bodyText.orEmpty().contains("\"databaseId\":\"db-1\""))
                assertTrue(request.bodyText.orEmpty().contains("\"accountTag\":\"acc-1\""))
                DemoReply.graphQl(buildJsonObject {
                    putJsonObject("viewer") {
                        putJsonArray("accounts") {
                            add(buildJsonObject {
                                putJsonArray("d1AnalyticsAdaptiveGroups") {
                                    add(day("2026-11-06", reads = 30, writes = 3, rowsRead = 900, rowsWritten = 9, p90 = 12.5))
                                    add(day("2026-11-04", reads = 10, writes = 1, rowsRead = 300, rowsWritten = 2, p90 = 8.0))
                                }
                            })
                        }
                    }
                })
            }
        }

        // Act
        val result = MetricsRepository(api).daily("acc-1", "db-1", START, END)

        // Assert
        val points = (result as ApiResult.Success).value
        assertEquals(listOf("2026-11-04", "2026-11-06"), points.map { it.date })
        assertEquals(30.0, points[1].readQueries, 0.0)
        assertEquals(900.0, points[1].rowsRead, 0.0)
        assertEquals(12.5, points[1].p90LatencyMs!!, 0.0)
    }

    @Test
    fun `a GraphQL error is reported as a GraphQL failure`() = runBlocking<Unit> {
        val api = mockCloudflareApi {
            post("/graphql") { DemoReply.graphQlErrors("unknown field") }
        }

        val result = MetricsRepository(api).daily("acc-1", "db-1", START, END)

        assertTrue((result as ApiResult.Failure).error is ApiError.GraphQl)
    }

    @Test
    fun `storage is the largest size seen in the window`() = runBlocking<Unit> {
        val api = mockCloudflareApi {
            post("/graphql") {
                DemoReply.graphQl(buildJsonObject {
                    putJsonObject("viewer") {
                        putJsonArray("accounts") {
                            add(buildJsonObject {
                                putJsonArray("d1StorageAdaptiveGroups") {
                                    add(buildJsonObject { putJsonObject("max") { put("databaseSizeBytes", 2048) } })
                                    add(buildJsonObject { putJsonObject("max") { put("databaseSizeBytes", 4096) } })
                                }
                            })
                        }
                    }
                })
            }
        }

        val bytes = MetricsRepository(api).storageBytes("acc-1", "db-1", START, END)

        assertEquals(4096L, (bytes as ApiResult.Success).value)
    }

    @Test
    fun `an empty window yields an empty series, not an error`() = runBlocking<Unit> {
        val api = mockCloudflareApi {
            post("/graphql") {
                DemoReply.graphQl(buildJsonObject {
                    putJsonObject("viewer") { putJsonArray("accounts") { add(buildJsonObject { putJsonArray("d1AnalyticsAdaptiveGroups") {} }) } }
                })
            }
        }

        val result = MetricsRepository(api).daily("acc-1", "db-1", START, END)

        assertEquals(emptyList<DailyPoint>(), (result as ApiResult.Success).value)
    }

    private fun day(date: String, reads: Int, writes: Int, rowsRead: Int, rowsWritten: Int, p90: Double) = buildJsonObject {
        putJsonObject("dimensions") { put("date", date) }
        putJsonObject("sum") {
            put("readQueries", reads)
            put("writeQueries", writes)
            put("rowsRead", rowsRead)
            put("rowsWritten", rowsWritten)
        }
        putJsonObject("quantiles") { put("queryBatchTimeMsP90", p90) }
    }

}
