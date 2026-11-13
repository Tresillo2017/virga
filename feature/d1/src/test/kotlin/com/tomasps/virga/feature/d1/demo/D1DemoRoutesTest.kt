package com.tomasps.virga.feature.d1.demo

import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.feature.d1.data.DatabasesRepository
import com.tomasps.virga.feature.d1.data.SqlRepository
import com.tomasps.virga.feature.d1.data.TableRowsRepository
import com.tomasps.virga.feature.d1.data.TableCatalogRepository
import com.tomasps.virga.feature.d1.sql.ColumnValue
import com.tomasps.virga.feature.d1.sql.updateStatement
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ACCOUNT = "acc-demo"
private const val ORDERS = "demo-db-orders"

class D1DemoRoutesTest {

    private val api = mockCloudflareApi { D1DemoRoutes().contribute(this) }

    @Test
    fun `the catalog lists the demo tables sorted and the schema reads primary keys`() = runBlocking<Unit> {
        val catalog = TableCatalogRepository(SqlRepository(api))

        val tables = (catalog.tables(ACCOUNT, ORDERS) as ApiResult.Success).value
        val schema = (catalog.schema(ACCOUNT, ORDERS, "orders") as ApiResult.Success).value

        assertEquals(listOf("audit_log", "customers", "orders"), tables)
        assertEquals(listOf("id"), schema.primaryKey)
        assertEquals("customers", schema.foreignKeys.single().table)
    }

    @Test
    fun `a demo insert, edit and delete change the grid the next read returns`() = runBlocking<Unit> {
        val rows = TableRowsRepository(SqlRepository(api))
        val before = (rows.page(ACCOUNT, ORDERS, "customers", offset = 0, pageRows = 50) as ApiResult.Success).value.rows.size

        rows.execute(ACCOUNT, ORDERS, com.tomasps.virga.feature.d1.sql.insertStatement("customers", listOf(ColumnValue("id", "4"), ColumnValue("name", "Dev Patel"))))
        rows.execute(ACCOUNT, ORDERS, updateStatement("customers", listOf(ColumnValue("region", "NA")), listOf(ColumnValue("id", "4"))))
        val afterInsert = (rows.page(ACCOUNT, ORDERS, "customers", offset = 0, pageRows = 50) as ApiResult.Success).value

        assertEquals(before + 1, afterInsert.rows.size)
        assertEquals(JsonPrimitive("NA"), afterInsert.rows.last()[2])

        rows.execute(ACCOUNT, ORDERS, com.tomasps.virga.feature.d1.sql.deleteStatement("customers", listOf(ColumnValue("id", "4"))))
        val afterDelete = (rows.page(ACCOUNT, ORDERS, "customers", offset = 0, pageRows = 50) as ApiResult.Success).value
        assertEquals(before, afterDelete.rows.size)
    }

    @Test
    fun `an unsupported statement is refused with a demo error instead of a fake result`() = runBlocking<Unit> {
        val result = SqlRepository(api).run(ACCOUNT, ORDERS, "CREATE TABLE t (x INTEGER)")

        assertTrue(result is ApiResult.Failure)
    }

    @Test
    fun `the paged select returns the requested window and reports whether more rows exist`() = runBlocking<Unit> {
        val rows = TableRowsRepository(SqlRepository(api))

        val page = (rows.page(ACCOUNT, ORDERS, "orders", offset = 1, pageRows = 1) as ApiResult.Success).value

        assertEquals(1, page.rows.size)
        assertEquals(JsonPrimitive(102), page.rows.single()[0])
        assertTrue(page.hasMore)
    }

    @Test
    fun `replication can be switched with PUT as the reference documents it`() = runBlocking<Unit> {
        val databases = DatabasesRepository(api)

        val updated = databases.setReadReplication(ACCOUNT, ORDERS, mode = "disabled")

        assertEquals("disabled", (updated as ApiResult.Success).value.readReplication?.mode)
    }

    @Test
    fun `a deleted demo database can be undeleted and then reads again`() = runBlocking<Unit> {
        val databases = DatabasesRepository(api)
        databases.delete(ACCOUNT, ORDERS)

        val restored = databases.undelete(ACCOUNT, ORDERS)
        val fetched = databases.get(ACCOUNT, ORDERS)

        assertTrue(restored is ApiResult.Success)
        assertTrue(fetched is ApiResult.Success)
    }

    @Test
    fun `undeleting a database that was never deleted is refused`() = runBlocking<Unit> {
        val result = DatabasesRepository(api).undelete(ACCOUNT, ORDERS)

        assertTrue(result is ApiResult.Failure)
    }

    @Test
    fun `the export answers its first call with a bookmark and the next poll with a link`() = runBlocking<Unit> {
        val export = com.tomasps.virga.feature.d1.data.ExportRepository(api, pause = {})

        val started = export.start(ACCOUNT, ORDERS, com.tomasps.virga.feature.d1.data.ExportOptions())
        val file = export.awaitCompletion(ACCOUNT, ORDERS, (started as ApiResult.Success).value, onProgress = {})

        assertTrue(file is ApiResult.Success)
    }
}
