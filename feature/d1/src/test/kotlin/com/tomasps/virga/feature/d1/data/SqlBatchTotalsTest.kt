package com.tomasps.virga.feature.d1.data

import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

private const val RAW = "/accounts/{accountId}/d1/database/{databaseId}/raw"

class SqlBatchTotalsTest {

    @Test
    fun `totals and the statement count cover every statement in a batch, not only the first`() = runBlocking<Unit> {
        // Arrange: two statements, each with its own meta
        val api = mockCloudflareApi {
            post(RAW) {
                DemoReply.ok(
                    buildJsonArray {
                        add(statement(rowsRead = 3.0, rowsWritten = 0.0, duration = 1.5))
                        add(statement(rowsRead = 0.0, rowsWritten = 2.0, duration = 2.5))
                    },
                )
            }
        }

        // Act
        val outcome = (SqlRepository(api).run(ACCOUNT, DATABASE, "SELECT 1; UPDATE t SET a = 1") as ApiResult.Success).value

        // Assert
        assertEquals(2, outcome.statementCount)
        assertEquals(3L, outcome.rowsRead)
        assertEquals(2L, outcome.rowsWritten)
        assertEquals(4.0, outcome.durationMs!!, 0.0001)
    }

    private fun statement(rowsRead: Double, rowsWritten: Double, duration: Double) = buildJsonObject {
        putJsonObject("results") {
            putJsonArray("columns") { add(JsonPrimitive("n")) }
            putJsonArray("rows") {}
        }
        putJsonObject("meta") {
            put("rows_read", rowsRead)
            put("rows_written", rowsWritten)
            putJsonObject("timings") { put("sql_duration_ms", duration) }
        }
    }

    private companion object {
        const val ACCOUNT = "acc-1"
        const val DATABASE = "db-1"
    }
}
