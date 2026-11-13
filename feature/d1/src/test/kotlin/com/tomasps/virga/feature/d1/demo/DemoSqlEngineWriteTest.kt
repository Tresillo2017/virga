package com.tomasps.virga.feature.d1.demo

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DemoSqlEngineWriteTest {

    private val database = seedDatabases().first { it.name == "orders-demo" }

    @Test
    fun `literal INSERT stores quoted text and numbers, keeping commas inside quotes`() {
        // Arrange
        val sql = "INSERT INTO orders (id, customer_id, total_cents, status) VALUES (104, 4, 999, 'open, late')"

        // Act
        val result = DemoSqlEngine.run(database, sql, emptyList())

        // Assert
        assertEquals(1L, result.rowsWritten)
        val inserted = result.updated!!.tables.getValue("orders").rows.last()
        assertEquals(
            listOf(JsonPrimitive(104), JsonPrimitive(4), JsonPrimitive(999), JsonPrimitive("open, late")),
            inserted,
        )
    }

    @Test
    fun `literal UPDATE with a literal WHERE changes only the matching row`() {
        // Arrange
        val sql = "UPDATE orders SET status = 'closed' WHERE id = 102"

        // Act
        val result = DemoSqlEngine.run(database, sql, emptyList())

        // Assert
        assertEquals(1L, result.rowsWritten)
        val rows = result.updated!!.tables.getValue("orders").rows
        assertEquals(JsonPrimitive("closed"), rows.first { it[0] == JsonPrimitive(102) }[3])
        assertEquals(JsonPrimitive("shipped"), rows.first { it[0] == JsonPrimitive(101) }[3])
    }

    @Test
    fun `literal INSERT with an unquoted non-numeric word is refused`() {
        // Arrange
        val sql = "INSERT INTO orders (id, status) VALUES (105, open)"

        // Act and Assert
        assertThrows(DemoSqlUnsupported::class.java) { DemoSqlEngine.run(database, sql, emptyList()) }
    }
}
