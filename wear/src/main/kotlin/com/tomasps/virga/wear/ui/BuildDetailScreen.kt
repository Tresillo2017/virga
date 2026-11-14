package com.tomasps.virga.wear.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.tomasps.virga.R
import com.tomasps.virga.core.common.builds.BuildSummary
import com.tomasps.virga.wear.present.tileFor
import com.tomasps.virga.wear.present.visualState
import androidx.core.content.ContextCompat
import java.time.Instant

/** One build, or the confirm, progress, failure or done screen for the action in flight. */
@Composable
fun BuildDetailScreen(
    state: DetailState,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onWatch: () -> Unit = {},
    onStopWatching: () -> Unit = {},
) {
    when (val action = state.action) {
        is ActionState.Confirming -> ConfirmScreen(action.kind, onConfirm, onDismiss)
        ActionState.Running -> MessageScreen(stringResource(R.string.action_running))
        is ActionState.Failed ->
            MessageScreen(action.message.phrase(), stringResource(R.string.confirm_back), onDismiss)
        ActionState.Done -> MessageScreen(stringResource(R.string.action_done), stringResource(R.string.action_ok), onDismiss)
        ActionState.Idle -> BuildFacts(state, onCancel, onRetry, onWatch, onStopWatching)
    }
}

@Composable
private fun BuildFacts(
    state: DetailState,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onWatch: () -> Unit,
    onStopWatching: () -> Unit,
) {
    val build = state.build
    val now = remember(build) { Instant.now() }
    val model = tileFor(build, fetchedAt = now, now = now)
    val listState = rememberScalingLazyListState()
    ScreenScaffold(scrollState = listState) { padding ->
        ScalingLazyColumn(state = listState, contentPadding = padding) {
            item { ListHeader { Text(build.scriptName, maxLines = 2) } }
            item {
                Text(
                    stringResource(R.string.status_with_age, build.visualState().label(), model.detail),
                    color = build.visualState().color(),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            commitLine(build)?.let { line ->
                item {
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            watchItems(state, onWatch, onStopWatching)
            if (state.canCancel) {
                item {
                    Button(
                        onClick = onCancel,
                        label = { Text(stringResource(R.string.action_cancel)) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (state.retryPlan != null) {
                item {
                    FilledTonalButton(
                        onClick = onRetry,
                        label = { Text(stringResource(R.string.action_retry)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

private fun commitLine(build: BuildSummary): String? =
    build.commitMessage?.takeIf { it.isNotBlank() } ?: build.branch?.takeIf { it.isNotBlank() }

/**
 * Watch or Stop watching. Watching starts whether or not the wearer allows notifications; the permission is asked once
 * on the tap, and a denial only means no alerts, which the screen says.
 */
private fun androidx.wear.compose.foundation.lazy.ScalingLazyListScope.watchItems(
    state: DetailState,
    onWatch: () -> Unit,
    onStopWatching: () -> Unit,
) {
    if (state.isWatching) {
        item { WatchingButton(onStopWatching) }
        item { AlertsOffNotice() }
    } else if (state.canWatch) {
        item { WatchButton(onWatch) }
        // Android refused to start the service: the build is not watched, so say alerts are off rather than crash.
        if (state.watchFailed) item { AlertsOffText() }
    }
}

@Composable
private fun WatchButton(onWatch: () -> Unit) {
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onWatch() }
    val context = LocalContext.current
    FilledTonalButton(
        onClick = {
            val isGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (isGranted) onWatch() else ask.launch(Manifest.permission.POST_NOTIFICATIONS)
        },
        label = { Text(stringResource(R.string.action_watch)) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun WatchingButton(onStopWatching: () -> Unit) {
    FilledTonalButton(
        onClick = onStopWatching,
        label = { Text(stringResource(R.string.action_stop_watching)) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AlertsOffNotice() {
    val context = LocalContext.current
    val isGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    if (!isGranted) AlertsOffText()
}

@Composable
private fun AlertsOffText() {
    Text(
        stringResource(R.string.watch_alerts_off),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}
