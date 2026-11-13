package com.tomasps.virga.feature.d1.data

import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.feature.d1.sql.BoundStatement
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val RAW = "/accounts/{accountId}/d1/database/{databaseId}/raw"
private const val ACCOUNT = "acc-1"
private const val DATABASE = "db-1"

class TableRowsRepositoryTest {

    @Test
    fun `a page asks for one extra row so the repository knows whether another page exists`() = runBlocking<Unit> {
        // Arrange
        val api = mockCloudflareApi {
            post(RAW) { request ->
                assertTrue(request.bodyText.orEmpty().contains("LIMIT 3 OFFSET 4"))
                rawReply(listOf("id"), listOf(listOf(5), listOf(6), listOf(7)))
            }
        }

        // Act
        val page = TableRowsRepository(SqlRepository(api)).page(ACCOUNT, DATABASE, "orders", offset = 4, pageRows = 2)

        // Assert
        val value = (page as ApiResult.Success).value
        assertEquals(2, value.rows.size)
        assertTrue(value.hasMore)
        assertEquals(listOf("id"), value.columns)
    }

    @Test
    fun `the last page reports no more rows`() = runBlocking<Unit> {
        val api = mockCloudflareApi { post(RAW) { rawReply(listOf("id"), listOf(listOf(1))) } }

        val value = (TableRowsRepository(SqlRepository(api)).page(ACCOUNT, DATABASE, "orders", offset = 0, pageRows = 2) as ApiResult.Success).value

        assertFalse(value.hasMore)
        assertEquals(1, value.rows.size)
    }

    @Test
    fun `page size is capped at the grid maximum`() = runBlocking<Unit> {
        val api = mockCloudflareApi {
            post(RAW) { request ->
                assertTrue(request.bodyText.orEmpty().contains("LIMIT ${D1Limits.MAX_GRID_PAGE_ROWS + 1} OFFSET 0"))
                rawReply(listOf("id"), emptyList())
            }
        }

        TableRowsRepository(SqlRepository(api)).page(ACCOUNT, DATABASE, "orders", offset = 0, pageRows = 10_000)
    }

    @Test
    fun `an edit runs its bound statement with the parameters it was built with`() = runBlocking<Unit> {
        // Arrange
        val statement = BoundStatement("UPDATE \"orders\" SET \"total\" = ? WHERE \"id\" = ?", listOf("9", "1"))
        val api = mockCloudflareApi {
            post(RAW) { request ->
                assertTrue(request.bodyText.orEmpty().contains("\"params\":[\"9\",\"1\"]"))
                rawReply(emptyList(), emptyList())
            }
        }

        // Act
        val result = TableRowsRepository(SqlRepository(api)).execute(ACCOUNT, DATABASE, statement)

        // Assert
        assertTrue(result is ApiResult.Success)
    }
}
