package com.tomasps.virga.feature.d1.detail.timetravel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.ui.components.KeyValueRow
import com.tomasps.virga.core.ui.components.SectionHeader
import com.tomasps.virga.core.ui.dialogs.TypedNameConfirmDialog
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.core.ui.state.UiStateContent
import com.tomasps.virga.core.ui.state.asString
import com.tomasps.virga.feature.d1.R

/** Callbacks from the time travel tab to its view model. */
data class TimeTravelCallbacks(
    val onRefresh: () -> Unit,
    val onLookupChanged: (String) -> Unit,
    val onLookUp: () -> Unit,
    val onRestoreBookmark: (String) -> Unit,
    val onRestoreTimestamp: () -> Unit,
    val onCancelRestore: () -> Unit,
    val onConfirmRestore: () -> Unit,
    val onUndo: () -> Unit,
    val onDismissActionError: () -> Unit,
)

@Composable
internal fun TimeTravelTab(state: TimeTravelUiState, callbacks: TimeTravelCallbacks) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        SectionHeader(title = stringResource(R.string.d1_time_travel_current))
        UiStateContent(state = state.currentBookmark, onRetry = callbacks.onRefresh) { bookmark ->
            KeyValueRow(key = stringResource(R.string.d1_bookmark_label), value = bookmark)
        }
        Text(stringResource(R.string.d1_time_travel_intro), style = MaterialTheme.typography.bodySmall)

        SectionHeader(title = stringResource(R.string.d1_time_travel_lookup))
        OutlinedTextField(
            value = state.lookupInput,
            onValueChange = callbacks.onLookupChanged,
            label = { Text(stringResource(R.string.d1_time_travel_timestamp_label)) },
            supportingText = { Text(stringResource(R.string.d1_time_travel_timestamp_hint)) },
            isError = state.lookupError != null,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth(),
        )
        state.lookupError?.let { Text(it.message.asString(), color = MaterialTheme.colorScheme.error) }
        Row2(
            onLookUp = callbacks.onLookUp,
            onRestore = callbacks.onRestoreTimestamp,
            canRestore = state.lookupInput.isNotBlank(),
        )
        state.lookupBookmark?.let { bookmark ->
            KeyValueRow(key = stringResource(R.string.d1_bookmark_label), value = bookmark)
            OutlinedButton(onClick = { callbacks.onRestoreBookmark(bookmark) }) {
                Text(stringResource(R.string.d1_time_travel_restore_bookmark))
            }
        }

        state.restoredMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        state.undoBookmark?.let {
            OutlinedButton(onClick = callbacks.onUndo) { Text(stringResource(R.string.d1_time_travel_undo)) }
        }
    }
    state.pendingRestore?.let { target ->
        val expected = state.databaseName.orEmpty()
        TypedNameConfirmDialog(
            title = stringResource(R.string.d1_restore_title),
            message = stringResource(R.string.d1_restore_message, describe(target)),
            expectedName = expected,
            onConfirm = callbacks.onConfirmRestore,
            onDismiss = callbacks.onCancelRestore,
            confirmLabel = stringResource(R.string.d1_restore_confirm),
        )
    }
    state.actionError?.let { error ->
        AlertDialog(
            onDismissRequest = callbacks.onDismissActionError,
            title = { Text(error.title.asString()) },
            text = { Text(error.message.asString()) },
            confirmButton = { TextButton(onClick = callbacks.onDismissActionError) { Text(stringResource(R.string.d1_ok)) } },
        )
    }
}

@Composable
private fun Row2(onLookUp: () -> Unit, onRestore: () -> Unit, canRestore: Boolean) {
    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onLookUp, enabled = canRestore) { Text(stringResource(R.string.d1_time_travel_lookup_action)) }
        Button(onClick = onRestore, enabled = canRestore) { Text(stringResource(R.string.d1_time_travel_restore_timestamp)) }
    }
}

@Composable
private fun describe(target: RestoreTarget): String = when (target) {
    is RestoreTarget.Bookmark -> stringResource(R.string.d1_restore_target_bookmark, target.bookmark)
    is RestoreTarget.Timestamp -> stringResource(R.string.d1_restore_target_timestamp, target.isoTimestamp)
}
