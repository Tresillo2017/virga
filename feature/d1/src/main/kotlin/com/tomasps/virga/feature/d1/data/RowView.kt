package com.tomasps.virga.feature.d1.data

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/** Reads named cells from a positional raw row. Missing columns and nulls read as null, never as an exception. */
internal class RowView(private val positions: Map<String, Int>, private val values: List<JsonElement>) {

    fun text(column: String): String? {
        val position = positions[column] ?: return null
        return when (val value = values.getOrNull(position)) {
            null, is JsonNull -> null
            is JsonPrimitive -> value.content
            else -> value.toString()
        }
    }

    fun int(column: String): Int? = text(column)?.toIntOrNull()

    fun flag(column: String): Boolean = int(column) == 1 || text(column) == "true"
}

/** Pairs each row with the column positions the statement returned. */
internal fun rowsOf(columns: List<String>, rows: List<List<JsonElement>>): List<RowView> {
    val positions = columns.withIndex().associate { (index, name) -> name to index }
    return rows.map { RowView(positions, it) }
}
