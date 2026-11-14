package com.tomasps.virga.shell

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.tomasps.virga.core.ui.window.findActivity

/**
 * While [enabled], hides [content] behind a lock screen until [authenticate] reports success. [authenticate] starts the
 * system prompt (biometric or screen lock) and calls its argument when the user is verified.
 *
 * The prompt starts once each time the lock screen appears, without a tap. Going to the background (ON_STOP) locks
 * the app again, but a configuration change (rotation, dark mode, font scale) does not: the verified flag is saved
 * across recreation. It is safe to save, because on Android 9 and newer the state is saved after ON_STOP has
 * already cleared it, so a restored process always starts locked.
 *
 * While locked, [content] leaves composition. Hiding it under an overlay is not enough: dialogs, menus and sheets are
 * separate windows that would stay on top of the lock screen. State that must survive a relock (the back stack) is
 * held above the gate.
 */
@Composable
fun AppLockGate(
    enabled: Boolean,
    authenticate: (onSuccess: () -> Unit) -> Unit,
    content: @Composable () -> Unit,
) {
    var verified by rememberSaveable { mutableStateOf(false) }
    RelockOnStop(enabled) { verified = false }
    if (!enabled || verified) {
        content()
    } else {
        val unlock = { authenticate { verified = true } }
        LaunchedEffect(Unit) { unlock() }
        LockScreen(onUnlock = unlock)
    }
}

/**
 * Runs [onStop] when the lifecycle stops and [enabled] is set, except when the activity is only being recreated for a
 * configuration change. The latest [onStop] is always the one called.
 */
@Composable
private fun RelockOnStop(enabled: Boolean, onStop: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val activity: Activity? = LocalContext.current.findActivity()
    val currentOnStop by rememberUpdatedState(onStop)
    DisposableEffect(lifecycle, enabled, activity) {
        val observer = LifecycleEventObserver { _, event ->
            val isRecreating = activity?.isChangingConfigurations == true
            if (enabled && event == Lifecycle.Event.ON_STOP && !isRecreating) currentOnStop()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}
