package com.tomasps.virga.feature.d1.detail.tables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.tomasps.virga.feature.d1.R

/**
 * Editor for one row: a text field per column and a NULL toggle. Save only builds the statement; the tables tab
 * shows it and asks for confirmation before anything runs. Key columns of an existing row are read-only.
 */
@Composable
internal fun RowEditorDialog(
    editor: RowEditorState,
    onCellText: (String, String) -> Unit,
    onCellNull: (String, Boolean) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (editor.isInsert) R.string.d1_row_insert_title else R.string.d1_row_edit_title))
        },
        text = { RowEditorFields(editor = editor, onCellText = onCellText, onCellNull = onCellNull) },
        confirmButton = { Button(onClick = onSave) { Text(stringResource(R.string.d1_row_review)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.d1_cancel)) } },
    )
}

/**
 * The fields of the row editor, without the dialog around them. Key columns of an existing row are read-only; every
 * other column has a text field and a NULL toggle.
 */
@Composable
internal fun RowEditorFields(
    editor: RowEditorState,
    onCellText: (String, String) -> Unit,
    onCellNull: (String, Boolean) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
    ) {
        editor.cells.forEach { cell ->
            CellEditor(
                cell = cell,
                readOnly = !editor.isInsert && cell.isKey,
                onText = { onCellText(cell.column, it) },
                onNull = { onCellNull(cell.column, it) },
            )
        }
    }
}

@Composable
private fun CellEditor(
    cell: CellDraft,
    readOnly: Boolean,
    onText: (String) -> Unit,
    onNull: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = if (cell.isNull) "" else cell.text,
            onValueChange = onText,
            label = { Text(cell.column) },
            placeholder = { if (cell.isNull) Text(stringResource(R.string.d1_cell_null)) },
            enabled = !readOnly && !cell.isNull,
            readOnly = readOnly,
            singleLine = true,
            textStyle = androidx.compose.material3.LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth(),
        )
        if (!readOnly) {
            FilterChip(
                selected = cell.isNull,
                onClick = { onNull(!cell.isNull) },
                label = { Text(stringResource(R.string.d1_cell_set_null)) },
            )
        }
    }
}
