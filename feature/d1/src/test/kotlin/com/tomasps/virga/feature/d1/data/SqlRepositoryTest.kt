package com.tomasps.virga.feature.d1.data

import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ACCOUNT = "acc-1"
private const val DATABASE = "db-1"
private const val RAW_PATH = "/accounts/{accountId}/d1/database/{databaseId}/raw"
private const val TOO_LARGE = "D1 query result exceeded the maximum response size limit of 50 MB"

class SqlRepositoryTest {

    @Test
    fun `raw rows, columns and meta are read from the first statement result`() = runBlocking<Unit> {
        // Arrange
        val api = mockCloudflareApi {
            post(RAW_PATH) { request ->
                assertTrue(request.bodyText.orEmpty().contains("\"sql\":\"SELECT id, name FROM t\""))
                DemoReply.ok(
                    buildJsonArray {
                        add(buildJsonObject {
                            putJsonObject("results") {
                                putJsonArray("columns") { add(JsonPrimitive("id")); add(JsonPrimitive("name")) }
                                putJsonArray("rows") {
                                    add(buildJsonArray { add(JsonPrimitive(1)); add(JsonPrimitive("Ada")) })
                                }
                            }
                            putJsonObject("meta") {
                                put("rows_read", 4)
                                put("rows_written", 0)
                                putJsonObject("timings") { put("sql_duration_ms", 1.5) }
                            }
                        })
                    },
                )
            }
        }

        // Act
        val result = SqlRepository(api).run(ACCOUNT, DATABASE, "SELECT id, name FROM t")

        // Assert
        val outcome = (result as ApiResult.Success).value
        assertEquals(listOf("id", "name"), outcome.columns)
        assertEquals(1, outcome.rows.size)
        assertEquals(4L, outcome.rowsRead)
        assertEquals(0L, outcome.rowsWritten)
        assertEquals(1.5, outcome.durationMs!!, 0.0001)
    }

    @Test
    fun `a write without rows still yields an outcome with empty columns`() = runBlocking<Unit> {
        val api = mockCloudflareApi {
            post(RAW_PATH) {
                DemoReply.ok(buildJsonArray { add(buildJsonObject { putJsonObject("meta") { put("rows_written", 1) } }) })
            }
        }

        val outcome = (SqlRepository(api).run(ACCOUNT, DATABASE, "DELETE FROM t WHERE id = ?", listOf("1")) as ApiResult.Success).value

        assertEquals(emptyList<String>(), outcome.columns)
        assertEquals(1L, outcome.rowsWritten)
    }

    @Test
    fun `an empty result array is a decode failure, not a crash`() = runBlocking<Unit> {
        val api = mockCloudflareApi { post(RAW_PATH) { DemoReply.ok(JsonArray(emptyList())) } }

        val result = SqlRepository(api).run(ACCOUNT, DATABASE, "SELECT 1")

        assertTrue(result is ApiResult.Failure && result.error is ApiError.Decode)
    }

    @Test
    fun `an oversized result surfaces its message so the UI can explain the 50 MB limit`() = runBlocking<Unit> {
        // Arrange
        val api = mockCloudflareApi {
            post(RAW_PATH) {
                DemoReply.failure(HttpStatusCode.BadRequest, 7500, TOO_LARGE)
            }
        }

        // Act
        val result = SqlRepository(api).run(ACCOUNT, DATABASE, "SELECT * FROM big")

        // Assert
        val error = (result as ApiResult.Failure).error
        assertTrue(error.messages.any { it.message.orEmpty().contains("maximum response size limit") })
    }

    @Test
    fun `a statement running past 30 seconds fails locally with the request limit reason`() = runBlocking<Unit> {
        // Arrange
        val api = mockCloudflareApi {
            post(RAW_PATH) {
                delay(D1_TIMEOUT_PROBE_MILLIS)
                DemoReply.ok(JsonArray(emptyList()))
            }
        }

        // Act
        val result = SqlRepository(api).run(ACCOUNT, DATABASE, "SELECT slow()")

        // Assert
        val error = (result as ApiResult.Failure).error
        assertEquals(ApiError.Local(D1Limits.REQUEST_LIMIT_REASON), error)
    }

    private companion object {
        const val D1_TIMEOUT_PROBE_MILLIS = 40_000L
    }
}
