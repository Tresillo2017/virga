package com.tomasps.virga.core.network.demo

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Flag the session layer flips when the built-in demo account is selected. */
class DemoMode(initiallyEnabled: Boolean = false) {
    private val mutableEnabled = MutableStateFlow(initiallyEnabled)

    val enabled: StateFlow<Boolean> = mutableEnabled.asStateFlow()

    fun setEnabled(isEnabled: Boolean) {
        mutableEnabled.value = isEnabled
    }
}
