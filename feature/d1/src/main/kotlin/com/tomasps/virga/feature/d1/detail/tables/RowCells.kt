package com.tomasps.virga.feature.d1.detail.tables

import com.tomasps.virga.core.ui.state.ErrorUi
import com.tomasps.virga.core.ui.state.UiText
import com.tomasps.virga.feature.d1.R
import com.tomasps.virga.feature.d1.data.TableSchema
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/** The text of a cell, or null for SQL NULL. Non-primitive values are shown as their JSON text. */
internal fun cellText(element: JsonElement?): String? = when (element) {
    null, is JsonNull -> null
    is JsonPrimitive -> element.content
    else -> element.toString()
}

/** The text of [column] in [row], or null when the column is missing or NULL. */
internal fun cellValue(columns: List<String>, row: List<JsonElement>, column: String): String? =
    cellText(columns.indexOf(column).takeIf { it >= 0 }?.let { row.getOrNull(it) })

/** Editor cells for an existing row: every column, with NULL kept as NULL so an edit does not write an empty string. */
internal fun rowCells(schema: TableSchema, columns: List<String>, row: List<JsonElement>): List<CellDraft> =
    columns.mapIndexed { index, column ->
        val text = cellText(row.getOrNull(index))
        CellDraft(
            column = column,
            text = text.orEmpty(),
            isNull = text == null,
            isKey = column in schema.primaryKey,
            original = text,
        )
    }

/**
 * Shown for a row holding arrays or objects. Their text form is not the stored value, so writing it back could
 * change the data; such a row is read-only in the grid.
 */
internal val rowNotEditableError: ErrorUi = ErrorUi(
    title = UiText.Resource(R.string.d1_error_row_not_editable_title),
    message = UiText.Resource(R.string.d1_error_row_not_editable_message),
    retryable = false,
)

/** Shown when a row has no usable key, so it cannot be changed or deleted safely. */
internal val rowKeyMissingError: ErrorUi = ErrorUi(
    title = UiText.Resource(R.string.d1_error_row_key_title),
    message = UiText.Resource(R.string.d1_error_row_key_message),
    retryable = false,
)
