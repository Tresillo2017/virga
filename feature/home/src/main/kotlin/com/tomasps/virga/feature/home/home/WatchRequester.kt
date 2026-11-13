package com.tomasps.virga.feature.home.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.tomasps.virga.core.common.builds.BuildSummary
import com.tomasps.virga.feature.home.R

/** Android 13 introduced the runtime POST_NOTIFICATIONS permission. */
internal const val POST_NOTIFICATIONS_SDK = 33

/**
 * Returns the Watch handler for Home. It reports whether the notification permission is still needed, so the
 * ViewModel can decide between showing the rationale and watching straight away.
 */
@Composable
internal fun rememberWatchRequest(onRequested: (BuildSummary, Boolean) -> Unit): (BuildSummary) -> Unit {
    val context = LocalContext.current
    val currentOnRequested by rememberUpdatedState(onRequested)
    return { build ->
        currentOnRequested(build, isNotificationPermissionNeeded(context.hasNotificationPermission()))
    }
}

/**
 * Shows the Watch steps that the ViewModel is in. The rationale offers "Continue" (asks for the permission) or
 * "Not now" (watches without asking). Dismissing it (back or a tap outside) cancels the Watch. The permission answer
 * always leads to watching, granted or not.
 */
@Composable
internal fun WatchPromptHost(
    prompt: WatchPrompt?,
    onRationaleAnswered: (Boolean) -> Unit,
    onRationaleDismissed: () -> Unit,
    onPermissionAnswered: () -> Unit,
) {
    val currentOnPermissionAnswered by rememberUpdatedState(onPermissionAnswered)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        currentOnPermissionAnswered()
    }
    when (prompt) {
        is WatchPrompt.Rationale -> WatchRationaleDialog(
            onContinue = { onRationaleAnswered(true) },
            onNotNow = { onRationaleAnswered(false) },
            onDismiss = onRationaleDismissed,
        )
        is WatchPrompt.AwaitingPermission -> LaunchedEffect(prompt) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        null -> Unit
    }
}

/** True when the rationale should be shown: Android 13 or later and the permission is not granted. */
internal fun isNotificationPermissionNeeded(isGranted: Boolean, sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
    sdkInt >= POST_NOTIFICATIONS_SDK && !isGranted

private fun android.content.Context.hasNotificationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

@Composable
private fun WatchRationaleDialog(onContinue: () -> Unit, onNotNow: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_watch_rationale_title)) },
        text = { Text(stringResource(R.string.home_watch_rationale_message)) },
        confirmButton = {
            TextButton(onClick = onContinue) { Text(stringResource(R.string.home_watch_rationale_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onNotNow) { Text(stringResource(R.string.home_watch_rationale_dismiss)) }
        },
    )
}
