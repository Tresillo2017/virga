package com.tomasps.virga.shell

import android.app.Activity
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.tomasps.virga.core.ui.window.SecureWindow
import com.tomasps.virga.core.ui.window.findActivity
import com.tomasps.virga.feature.account.settings.isDeviceSecure

/**
 * Whether the device has a screen lock or biometrics right now. Read again each time the app resumes, so removing the
 * screen lock in system settings is noticed when the user comes back. App lock only engages while this is true: with
 * nothing to verify against, a lock would have no way out.
 */
@Composable
fun rememberDeviceSecure(): Boolean {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var secure by remember { mutableStateOf(isDeviceSecure(context)) }
    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) secure = isDeviceSecure(context)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return secure
}

/**
 * Keeps the app's content out of the Recents preview while the app lock is engaged, so the thumbnail cannot show what
 * the lock hides. Android 13 and newer can drop only the Recents screenshot; older releases need the secure flag, which
 * also blocks screenshots while app lock is on.
 */
@Composable
fun HideFromRecents(enabled: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val activity: Activity? = LocalContext.current.findActivity()
        DisposableEffect(activity, enabled) {
            activity?.setRecentsScreenshotEnabled(!enabled)
            onDispose { activity?.setRecentsScreenshotEnabled(true) }
        }
    } else {
        SecureWindow(enabled = enabled)
    }
}
