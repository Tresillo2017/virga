package com.tomasps.virga.core.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.ui.R

private val DIALOG_GAP = 12.dp

/**
 * Plain confirmation for reversible or low-stakes actions: delete a DNS record, cancel a build, purge cache.
 * Set [destructive] to colour the confirm button in the error colour.
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmLabel: String = stringResource(R.string.core_ui_confirm),
    dismissLabel: String = stringResource(R.string.core_ui_cancel),
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm, colors = confirmColors(destructive)) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissLabel)
            }
        },
    )
}

/**
 * Confirmation that requires typing [expectedName] exactly. Used for the irreversible actions: delete a Worker,
 * Pages project, zone, bucket, database or KV namespace, and D1 time-travel restore.
 * The confirm button stays disabled until the text matches (see [isTypedNameMatch]).
 */
@Composable
fun TypedNameConfirmDialog(
    title: String,
    message: String,
    expectedName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmLabel: String = stringResource(R.string.core_ui_confirm),
    dismissLabel: String = stringResource(R.string.core_ui_cancel),
) {
    var typed by rememberSaveable(expectedName) { mutableStateOf("") }
    val matches = remember(typed, expectedName) { isTypedNameMatch(typed, expectedName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(title) },
        text = {
            Column {
                Text(message)
                Spacer(Modifier.height(DIALOG_GAP))
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.core_ui_type_to_confirm, expectedName)) },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (matches) onConfirm() },
                enabled = matches,
                colors = confirmColors(destructive = true),
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissLabel)
            }
        },
    )
}

@Composable
private fun confirmColors(destructive: Boolean) = if (destructive) {
    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
} else {
    ButtonDefaults.textButtonColors()
}
