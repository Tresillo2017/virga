package com.tomasps.virga.feature.d1.data

import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val RAW = "/accounts/{accountId}/d1/database/{databaseId}/raw"

class TableRowsOrderTest {

    @Test
    fun `a page is ordered by the primary key so offsets are stable`() = runBlocking<Unit> {
        var sent = ""
        val api = mockCloudflareApi {
            post(RAW) { request ->
                sent = request.bodyText.orEmpty()
                rawReply(listOf("id"), listOf(listOf(1)))
            }
        }

        val page = TableRowsRepository(SqlRepository(api)).page("acc-1", "db-1", "orders", offset = 0, pageRows = 10, orderBy = listOf("id"))

        assertTrue(page is ApiResult.Success)
        assertTrue(sent.contains("ORDER BY \\\"id\\\" LIMIT 11 OFFSET 0"))
    }

    @Test
    fun `a table without a key is ordered by rowid`() = runBlocking<Unit> {
        var sent = ""
        val api = mockCloudflareApi {
            post(RAW) { request ->
                sent = request.bodyText.orEmpty()
                rawReply(listOf("id"), listOf(listOf(1)))
            }
        }

        TableRowsRepository(SqlRepository(api)).page("acc-1", "db-1", "audit", offset = 0, pageRows = 10)

        assertTrue(sent.contains("ORDER BY rowid"))
    }
}
