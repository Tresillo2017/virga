package com.tomasps.virga.feature.pages.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Calls [onReturn] each time the screen resumes. The first resume is not skipped: navigation may dispose and
 * recreate this composition while the view model survives, so a per-composition "first resume" flag would leave
 * a deleted project in the list. The extra load on first open is cheap next to showing stale data.
 */
@Composable
internal fun RefreshOnReturn(onReturn: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    val currentOnReturn by rememberUpdatedState(onReturn)
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) currentOnReturn()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}
