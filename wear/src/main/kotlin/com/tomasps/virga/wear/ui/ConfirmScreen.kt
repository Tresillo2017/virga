package com.tomasps.virga.wear.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.tomasps.virga.R

/** Asks before Cancel or Retry makes its call. Nothing is sent until [onConfirm]. */
@Composable
fun ConfirmScreen(kind: ActionKind, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val title = when (kind) {
        ActionKind.Cancel -> R.string.confirm_cancel_title
        ActionKind.Retry -> R.string.confirm_retry_title
    }
    ConfirmScreen(title, onConfirm, onDismiss)
}

/** A question with Confirm and Back. Nothing happens until [onConfirm]. */
@Composable
fun ConfirmScreen(@StringRes title: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val listState = rememberScalingLazyListState()
    ScreenScaffold(scrollState = listState) { padding ->
        ScalingLazyColumn(state = listState, contentPadding = padding) {
            item { Text(stringResource(title), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
            item {
                Button(
                    onClick = onConfirm,
                    label = { Text(stringResource(R.string.confirm_yes)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                FilledTonalButton(
                    onClick = onDismiss,
                    label = { Text(stringResource(R.string.confirm_back)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
