package com.tomasps.virga.feature.d1.data

import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val RAW = "/accounts/{accountId}/d1/database/{databaseId}/raw"
private const val ACCOUNT = "acc-1"
private const val DATABASE = "db-1"

class TableCatalogRepositoryTest {

    @Test
    fun `table list excludes internal tables and comes back sorted`() = runBlocking<Unit> {
        // Arrange
        val api = mockCloudflareApi {
            post(RAW) { request ->
                assertTrue(request.bodyText.orEmpty().contains("sqlite_master"))
                rawReply(listOf("name"), listOf(listOf("customers"), listOf("orders")))
            }
        }

        // Act
        val tables = TableCatalogRepository(SqlRepository(api)).tables(ACCOUNT, DATABASE)

        // Assert
        assertEquals(listOf("customers", "orders"), (tables as ApiResult.Success).value)
    }

    @Test
    fun `schema reads columns with their primary key position, indexes and foreign keys`() = runBlocking<Unit> {
        // Arrange
        val api = mockCloudflareApi {
            post(RAW) { request ->
                val body = request.bodyText.orEmpty()
                when {
                    body.contains("pragma_table_info") -> rawReply(
                        listOf("name", "type", "notnull", "dflt_value", "pk"),
                        listOf(
                            listOf("order_id", "INTEGER", 1, null, 1),
                            listOf("line", "INTEGER", 1, "1", 2),
                            listOf("note", "TEXT", 0, null, 0),
                        ),
                    )
                    body.contains("pragma_index_list") -> rawReply(
                        listOf("index_name", "is_unique", "column_name"),
                        listOf(listOf("idx_note", 0, "note")),
                    )
                    body.contains("pragma_foreign_key_list") -> rawReply(
                        listOf("from", "table", "to", "on_delete"),
                        listOf(listOf("order_id", "orders", "id", "CASCADE")),
                    )
                    else -> error("unexpected statement: $body")
                }
            }
        }

        // Act
        val schema = (TableCatalogRepository(SqlRepository(api)).schema(ACCOUNT, DATABASE, "order_items") as ApiResult.Success).value

        // Assert
        assertEquals(listOf("order_id", "line"), schema.primaryKey)
        assertTrue(schema.columns.first { it.name == "line" }.notNull)
        assertEquals("1", schema.columns.first { it.name == "line" }.defaultValue)
        assertEquals("idx_note", schema.indexes.single().name)
        assertFalse(schema.indexes.single().unique)
        assertEquals("orders", schema.foreignKeys.single().table)
        assertEquals("CASCADE", schema.foreignKeys.single().onDelete)
    }

    @Test
    fun `a failed schema query is reported, not partially returned`() = runBlocking<Unit> {
        val api = mockCloudflareApi {
            post(RAW) { DemoReply.failure(HttpStatusCode.InternalServerError, 1000, "boom") }
        }

        val result = TableCatalogRepository(SqlRepository(api)).schema(ACCOUNT, DATABASE, "t")

        assertTrue(result is ApiResult.Failure)
    }
}
