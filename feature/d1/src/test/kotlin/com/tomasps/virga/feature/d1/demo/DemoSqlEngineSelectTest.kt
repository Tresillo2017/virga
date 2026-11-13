package com.tomasps.virga.feature.d1.demo

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoSqlEngineSelectTest {

    private val database = seedDatabases().first { it.name == "orders-demo" }

    @Test
    fun `bare table name with a WHERE equality returns only matching rows`() {
        // Arrange
        val sql = "SELECT id, status FROM orders WHERE status = 'open'"

        // Act
        val result = DemoSqlEngine.run(database, sql, emptyList())

        // Assert
        assertEquals(listOf("id", "status"), result.columns)
        assertTrue(result.rows.isNotEmpty())
        assertTrue(result.rows.all { it[1] == JsonPrimitive("open") })
    }

    @Test
    fun `select list projects only the named columns in the given order`() {
        // Arrange
        val sql = "SELECT status, id FROM orders"

        // Act
        val result = DemoSqlEngine.run(database, sql, emptyList())

        // Assert
        assertEquals(listOf("status", "id"), result.columns)
        assertEquals(JsonPrimitive(101), result.rows.first()[1])
    }

    @Test
    fun `order by DESC sorts numerically`() {
        // Arrange
        val sql = "SELECT id FROM orders ORDER BY id DESC"

        // Act
        val ids = DemoSqlEngine.run(database, sql, emptyList()).rows.map { it[0] }

        // Assert
        assertEquals(ids.sortedBy { (it as JsonPrimitive).content.toInt() }.reversed(), ids)
    }

    @Test
    fun `rowid order and a multi-column order by are accepted`() {
        // Arrange
        val rowid = "SELECT * FROM orders ORDER BY rowid LIMIT 2 OFFSET 0"
        val multi = "SELECT id FROM orders ORDER BY status, id DESC"

        // Act
        val byRowid = DemoSqlEngine.run(database, rowid, emptyList())
        val byTwo = DemoSqlEngine.run(database, multi, emptyList())

        // Assert
        assertEquals(2, byRowid.rows.size)
        assertEquals(database.tables.getValue("orders").rows.size, byTwo.rows.size)
    }

    @Test
    fun `unsupported extra predicate is refused rather than ignored`() {
        // Arrange
        val sql = "SELECT id FROM orders WHERE status = 'open' AND id = 101"

        // Act / Assert
        assertThrows(DemoSqlUnsupported::class.java) { DemoSqlEngine.run(database, sql, emptyList()) }
    }

    @Test
    fun `unknown column in the select list is refused`() {
        // Arrange
        val sql = "SELECT nope FROM orders"

        // Act / Assert
        assertThrows(DemoSqlUnsupported::class.java) { DemoSqlEngine.run(database, sql, emptyList()) }
    }

    @Test
    fun `null cells are not matched by an equality literal`() {
        // Arrange
        val sql = "SELECT id FROM orders WHERE status = 'missing-value'"

        // Act
        val result = DemoSqlEngine.run(database, sql, emptyList())

        // Assert
        assertEquals(emptyList<List<kotlinx.serialization.json.JsonElement>>(), result.rows)
    }

    @Test
    fun `console delete with a literal WHERE removes only the matching rows`() {
        // Arrange
        val sql = "DELETE FROM orders WHERE id = 101"
        val before = database.tables.getValue("orders").rows.size

        // Act
        val result = DemoSqlEngine.run(database, sql, emptyList())

        // Assert
        assertEquals(1L, result.rowsWritten)
        assertEquals(before - 1, result.updated!!.tables.getValue("orders").rows.size)
    }
}
