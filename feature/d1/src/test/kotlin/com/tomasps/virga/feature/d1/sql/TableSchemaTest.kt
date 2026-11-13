package com.tomasps.virga.feature.d1.sql

import com.tomasps.virga.feature.d1.data.ColumnInfo
import com.tomasps.virga.feature.d1.data.TableSchema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TableSchemaTest {

    @Test
    fun `composite primary key is ordered by its position, not by column order`() {
        // Arrange
        val schema = schemaWith(
            column("line", pk = 2),
            column("note"),
            column("order_id", pk = 1),
        )

        // Act / Assert
        assertEquals(listOf("order_id", "line"), schema.primaryKey)
        assertTrue(schema.canEditRows)
    }

    @Test
    fun `table without a primary key is browse-only`() {
        // Arrange
        val schema = schemaWith(column("a"), column("b"))

        // Act / Assert
        assertEquals(emptyList<String>(), schema.primaryKey)
        assertFalse(schema.canEditRows)
    }

    private fun column(name: String, pk: Int = 0) = ColumnInfo(
        name = name,
        type = "TEXT",
        notNull = false,
        defaultValue = null,
        pkPosition = pk,
    )

    private fun schemaWith(vararg columns: ColumnInfo) = TableSchema(
        name = "t",
        columns = columns.toList(),
        indexes = emptyList(),
        foreignKeys = emptyList(),
    )
}
