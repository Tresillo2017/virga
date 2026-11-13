package com.tomasps.virga.feature.workers.detail

import android.Manifest
import android.os.Build
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.tomasps.virga.feature.workers.R

/** Android 13 introduced the runtime POST_NOTIFICATIONS permission. */
internal const val POST_NOTIFICATIONS_SDK = 33

/** True when the rationale should come first: Android 13 or later and the permission is not granted yet. */
internal fun isNotificationPermissionNeeded(isGranted: Boolean, sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
    sdkInt >= POST_NOTIFICATIONS_SDK && !isGranted

/** The permission the rationale leads to. */
internal const val NOTIFICATION_PERMISSION = Manifest.permission.POST_NOTIFICATIONS

/**
 * Explains why Virga asks for notifications before the system prompt, the same copy Home uses. "Continue" asks for
 * the permission; "Not now" watches the build without notifications. Either way the build is watched.
 */
@Composable
internal fun WatchRationaleDialog(onContinue: () -> Unit, onNotNow: () -> Unit) {
    AlertDialog(
        onDismissRequest = onNotNow,
        title = { Text(stringResource(R.string.workers_watch_rationale_title)) },
        text = { Text(stringResource(R.string.workers_watch_rationale_message)) },
        confirmButton = {
            TextButton(onClick = onContinue) { Text(stringResource(R.string.workers_watch_rationale_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onNotNow) { Text(stringResource(R.string.workers_watch_rationale_dismiss)) }
        },
    )
}
