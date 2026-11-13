package com.tomasps.virga.feature.d1.demo

import kotlinx.serialization.json.JsonElement

/** Canned schema for one demo column, shaped like the rows of PRAGMA table_info. */
internal data class DemoColumn(
    val name: String,
    val type: String,
    val notNull: Boolean = false,
    val defaultValue: String? = null,
    val pkPosition: Int = 0,
)

internal data class DemoIndex(val name: String, val unique: Boolean, val columns: List<String>)

internal data class DemoForeignKey(val from: String, val table: String, val to: String, val onDelete: String)

/** A demo table. Rows are positional and follow [columns]; the demo SQL engine reads and writes them. */
internal data class DemoTable(
    val columns: List<DemoColumn>,
    val rows: List<List<JsonElement>>,
    val indexes: List<DemoIndex> = emptyList(),
    val foreignKeys: List<DemoForeignKey> = emptyList(),
)

/** A demo database. Immutable: every write replaces the value in the store. */
internal data class DemoDatabase(
    val uuid: String,
    val name: String,
    val createdAt: String,
    val jurisdiction: String?,
    val readReplicationMode: String,
    val tables: Map<String, DemoTable>,
) {
    val sizeBytes: Long get() = tables.values.sumOf { it.rows.size.toLong() } * ROW_BYTES_ESTIMATE + BASE_BYTES

    private companion object {
        const val ROW_BYTES_ESTIMATE = 96L
        const val BASE_BYTES = 12_288L
    }
}
