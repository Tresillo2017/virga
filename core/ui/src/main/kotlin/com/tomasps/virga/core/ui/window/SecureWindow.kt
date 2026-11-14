package com.tomasps.virga.core.ui.window

import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import java.util.WeakHashMap

/**
 * While [enabled], the hosting activity's window is marked secure: no screenshots, and a blank preview in Recents.
 *
 * Several callers can ask at once (the "Block screenshots" setting, the sign-in screen while the token is shown). The
 * window stays secure while any of them asks, and the flag is cleared only when the last one stops asking or leaves
 * composition.
 */
@Composable
fun SecureWindow(enabled: Boolean) {
    val window = LocalContext.current.findActivity()?.window
    DisposableEffect(window, enabled) {
        if (window == null || !enabled) return@DisposableEffect onDispose { }
        SecureRequests.acquire(window)
        onDispose { SecureRequests.release(window) }
    }
}

/** Counts the active secure requests per window. Main thread only, like every composition effect. */
private object SecureRequests {
    private val counts = WeakHashMap<Window, Int>()

    fun acquire(window: Window) {
        val count = counts.getOrDefault(window, 0) + 1
        counts[window] = count
        if (count == 1) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    fun release(window: Window) {
        val count = counts.getOrDefault(window, 0) - 1
        if (count > 0) {
            counts[window] = count
        } else {
            counts.remove(window)
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
