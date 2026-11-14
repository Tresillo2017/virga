package com.tomasps.virga.shell

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.clearAndSetSemantics
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
 * While locked, [content] stays composed so the back stack and screen state survive a relock, but it is not drawn,
 * takes no input and is removed from the accessibility tree; the lock screen sits on top of it.
 */
@Composable
fun AppLockGate(
    enabled: Boolean,
    authenticate: (onSuccess: () -> Unit) -> Unit,
    content: @Composable () -> Unit,
) {
    var verified by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    RelockOnStop(enabled) {
        verified = false
        focus.clearFocus(force = true)
    }
    val locked = enabled && !verified
    Box(Modifier.fillMaxSize()) {
        Box(if (locked) Modifier.fillMaxSize().hiddenWhileLocked() else Modifier.fillMaxSize()) {
            content()
        }
        if (locked) {
            val unlock = { authenticate { verified = true } }
            LaunchedEffect(Unit) { unlock() }
            LockScreen(onUnlock = unlock)
        }
    }
}

/** Not drawn and invisible to accessibility services. The lock screen drawn over it takes every touch. */
private fun Modifier.hiddenWhileLocked(): Modifier = graphicsLayer { alpha = 0f }.clearAndSetSemantics { }

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
