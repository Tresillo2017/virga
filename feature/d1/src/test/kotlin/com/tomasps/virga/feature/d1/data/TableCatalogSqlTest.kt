package com.tomasps.virga.feature.d1.data

import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val RAW = "/accounts/{accountId}/d1/database/{databaseId}/raw"

class TableCatalogSqlTest {

    @Test
    fun `the internal-table filter uses prefix checks, so an underscore cannot match any character`() = runBlocking<Unit> {
        // Arrange: capture the statement the catalog sends
        var sent = ""
        val api = mockCloudflareApi {
            post(RAW) { request ->
                sent = request.bodyText.orEmpty()
                rawReply(listOf("name"), listOf(listOf("d1x"), listOf("sqliteX"), listOf("_cfx_log")))
            }
        }

        // Act
        val tables = (TableCatalogRepository(SqlRepository(api)).tables("acc-1", "db-1") as ApiResult.Success).value

        // Assert: the statement has no LIKE, so user tables named like the prefixes are listed
        assertFalse(sent.contains("LIKE"))
        assertTrue(sent.contains("substr(name, 1, 7) <> 'sqlite_'"))
        assertEquals(listOf("d1x", "sqliteX", "_cfx_log"), tables)
    }

}
