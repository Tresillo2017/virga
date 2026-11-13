package com.tomasps.virga.feature.kv.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.tomasps.virga.feature.kv.R
import com.tomasps.virga.feature.kv.data.KvJurisdiction
import com.tomasps.virga.feature.kv.value.KvExpirationDraft

private val DIALOG_FIELD_TOP = 8.dp

/** One text field in a dialog, for a name or a title. The confirm button is off while the field is blank. */
@Composable
internal fun KvTextInputDialog(
    title: String,
    label: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    supporting: String? = null,
) {
    var text by rememberSaveable(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(label) },
                supportingText = supporting?.let { { Text(it) } },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.kv_action_cancel)) }
        },
    )
}

/** JSON metadata editor. Blank text means no metadata. The check happens when the user saves. */
@Composable
internal fun KvMetadataDialog(
    text: String,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.kv_metadata_editor_title)) },
        text = {
            Column {
                Text(stringResource(R.string.kv_metadata_editor_message))
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier.fillMaxWidth().padding(top = DIALOG_FIELD_TOP),
                    minLines = METADATA_MIN_LINES,
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                    label = { Text(stringResource(R.string.kv_metadata_label)) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) { Text(stringResource(R.string.kv_action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.kv_action_cancel)) }
        },
    )
}

/** Expiration editor for an existing key. Saving rewrites the value, so the dialog says so. */
@Composable
internal fun KvExpirationDialog(
    draft: KvExpirationDraft,
    onDraftChange: (KvExpirationDraft) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.kv_expiration_editor_title)) },
        text = {
            Column {
                Text(stringResource(R.string.kv_expiration_editor_message))
                KvExpirationFields(draft = draft, onChange = onDraftChange, modifier = Modifier.padding(top = DIALOG_FIELD_TOP))
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) { Text(stringResource(R.string.kv_action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.kv_action_cancel)) }
        },
    )
}

private const val METADATA_MIN_LINES = 4

/** Create-namespace dialog: a title and the data location, which cannot be changed later. */
@Composable
internal fun KvCreateNamespaceDialog(
    onConfirm: (String, KvJurisdiction?) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var jurisdiction by rememberSaveable { mutableStateOf<KvJurisdiction?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.kv_create_namespace_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.kv_namespace_title_label)) },
                    supportingText = { Text(stringResource(R.string.kv_namespace_title_hint)) },
                )
                Text(
                    text = stringResource(R.string.kv_jurisdiction_heading),
                    modifier = Modifier.padding(top = DIALOG_FIELD_TOP),
                    style = MaterialTheme.typography.titleSmall,
                )
                JurisdictionOption(stringResource(R.string.kv_jurisdiction_default), jurisdiction == null) { jurisdiction = null }
                KvJurisdiction.entries.forEach { option ->
                    JurisdictionOption(jurisdictionLabel(option), jurisdiction == option) { jurisdiction = option }
                }
                Text(
                    text = stringResource(R.string.kv_jurisdiction_hint),
                    modifier = Modifier.padding(top = DIALOG_FIELD_TOP),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title.trim(), jurisdiction) }, enabled = title.isNotBlank()) {
                Text(stringResource(R.string.kv_action_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.kv_action_cancel)) }
        },
    )
}

@Composable
private fun JurisdictionOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label)
    }
}

@Composable
private fun jurisdictionLabel(option: KvJurisdiction): String = stringResource(
    when (option) {
        KvJurisdiction.EU -> R.string.kv_jurisdiction_eu
        KvJurisdiction.FEDRAMP -> R.string.kv_jurisdiction_fedramp
        KvJurisdiction.US -> R.string.kv_jurisdiction_us
    },
)
