@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.tomasps.virga.feature.account.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.tomasps.virga.core.ui.components.TimeRange
import com.tomasps.virga.core.ui.components.TimeRangePicker
import com.tomasps.virga.core.ui.dialogs.ConfirmDialog
import com.tomasps.virga.feature.account.R

/** The settings screen's dialogs. At most one is shown at a time, as the ViewModel's [SettingsUiState.activeDialog] says. */
@Composable
fun SettingsDialogs(state: SettingsUiState, viewModel: SettingsViewModel) {
    if (state.isTimeRangeDialogVisible) {
        TimeRangeDialog(
            selected = state.defaultTimeRange,
            onSelected = viewModel::onTimeRangeSelected,
            onDismiss = viewModel::onTimeRangeDismissed,
        )
    }
    if (state.isResetConfirmVisible) {
        ConfirmDialog(
            title = stringResource(R.string.settings_reset_title),
            message = stringResource(R.string.settings_reset_message),
            onConfirm = viewModel::onResetConfirmed,
            onDismiss = viewModel::onResetDismissed,
            confirmLabel = stringResource(R.string.settings_reset_confirm),
            dismissLabel = stringResource(R.string.settings_cancel),
            destructive = true,
        )
    }
    if (state.isSignOutConfirmVisible) {
        ConfirmDialog(
            title = stringResource(R.string.settings_sign_out_title),
            message = stringResource(R.string.settings_sign_out_message),
            onConfirm = viewModel::onSignOutConfirmed,
            onDismiss = viewModel::onSignOutDismissed,
            confirmLabel = stringResource(R.string.settings_sign_out_confirm),
            dismissLabel = stringResource(R.string.settings_cancel),
            destructive = true,
        )
    }
}

/** Picks the analytics window. Choosing a segment saves it and closes the dialog. */
@Composable
private fun TimeRangeDialog(
    selected: TimeRange,
    onSelected: (TimeRange) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_default_range)) },
        text = { TimeRangePicker(selected = selected, onSelected = onSelected) },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    )
}
