package com.tomasps.virga.feature.builds.watch

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the summary notification may be posted again. Once the user dismisses it, it is never reposted until the
 * user starts a new watch. Plain Kotlin, so the rule is tested without Android.
 */
class SummaryDismissal {
    private val mutableDismissed = MutableStateFlow(false)

    val dismissed: StateFlow<Boolean> = mutableDismissed.asStateFlow()

    fun onDismissed() {
        mutableDismissed.value = true
    }

    /** A new watch was started from the user's tap, so the summary is shown again. */
    fun onNewWatch() {
        mutableDismissed.value = false
    }

    fun mayRepost(): Boolean = !mutableDismissed.value
}
