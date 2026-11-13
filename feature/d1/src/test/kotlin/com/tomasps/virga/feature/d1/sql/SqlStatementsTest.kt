package com.tomasps.virga.feature.d1.sql

import org.junit.Assert.assertEquals
import org.junit.Test

class SqlStatementsTest {

    @Test
    fun `identifiers are quoted and embedded quotes are doubled`() {
        // Arrange
        val name = "weird\"name"

        // Act
        val quoted = quoteIdentifier(name)

        // Assert
        assertEquals("\"weird\"\"name\"", quoted)
    }

    @Test
    fun `page select uses integer literals for limit and offset and no parameters`() {
        // Arrange / Act
        val statement = selectPage(table = "orders", orderBy = listOf("id"), limit = 51, offset = 100)

        // Assert
        assertEquals("SELECT * FROM \"orders\" ORDER BY \"id\" LIMIT 51 OFFSET 100", statement)
    }

    @Test
    fun `a composite primary key orders by every key column in declared order`() {
        val statement = selectPage(table = "lines", orderBy = listOf("order_id", "line_no"), limit = 10, offset = 0)

        assertEquals("SELECT * FROM \"lines\" ORDER BY \"order_id\", \"line_no\" LIMIT 10 OFFSET 0", statement)
    }

    @Test
    fun `a table without a primary key orders by rowid so pages are stable`() {
        val statement = selectPage(table = "audit", orderBy = emptyList(), limit = 10, offset = 20)

        assertEquals("SELECT * FROM \"audit\" ORDER BY rowid LIMIT 10 OFFSET 20", statement)
    }

    @Test
    fun `insert binds every non-null value in column order and writes NULL literally`() {
        // Arrange
        val values = listOf(
            ColumnValue("name", "Ada"),
            ColumnValue("note", null),
            ColumnValue("total", "42"),
        )

        // Act
        val statement = insertStatement("orders", values)

        // Assert
        assertEquals("INSERT INTO \"orders\" (\"name\", \"note\", \"total\") VALUES (?, NULL, ?)", statement.sql)
        assertEquals(listOf("Ada", "42"), statement.params)
    }

    @Test
    fun `insert with no values uses DEFAULT VALUES`() {
        // Arrange / Act
        val statement = insertStatement("orders", emptyList())

        // Assert
        assertEquals("INSERT INTO \"orders\" DEFAULT VALUES", statement.sql)
        assertEquals(emptyList<String>(), statement.params)
    }

    @Test
    fun `update lists SET columns first, then the WHERE keys, with parameters in the same order`() {
        // Arrange
        val set = listOf(ColumnValue("name", "Grace"), ColumnValue("note", null))
        val where = listOf(ColumnValue("id", "7"))

        // Act
        val statement = updateStatement("customers", set, where)

        // Assert
        assertEquals("UPDATE \"customers\" SET \"name\" = ?, \"note\" = NULL WHERE \"id\" = ?", statement.sql)
        assertEquals(listOf("Grace", "7"), statement.params)
    }

    @Test
    fun `delete joins composite keys with AND`() {
        // Arrange
        val where = listOf(ColumnValue("order_id", "1"), ColumnValue("line", "2"))

        // Act
        val statement = deleteStatement("order_items", where)

        // Assert
        assertEquals("DELETE FROM \"order_items\" WHERE \"order_id\" = ? AND \"line\" = ?", statement.sql)
        assertEquals(listOf("1", "2"), statement.params)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `delete without a key is refused so it cannot empty a table`() {
        deleteStatement("orders", emptyList())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `update without a key is refused`() {
        updateStatement("orders", listOf(ColumnValue("name", "x")), emptyList())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `update without changed values is refused`() {
        updateStatement("orders", emptyList(), listOf(ColumnValue("id", "1")))
    }
}
