package com.tomasps.virga.feature.kv.changes

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tells open KV screens that namespaces or keys changed, so a list behind a pushed screen reloads when the user comes
 * back. A counter rather than an event, so a late collector still sees that something changed.
 */
class KvNamespaceChanges {
    private val mutableVersion = MutableStateFlow(0)
    val version: StateFlow<Int> = mutableVersion.asStateFlow()

    fun notifyChanged() {
        mutableVersion.value = mutableVersion.value + 1
    }
}
