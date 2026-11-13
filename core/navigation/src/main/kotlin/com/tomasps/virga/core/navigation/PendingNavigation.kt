package com.tomasps.virga.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Keys that arrived from outside the app (an intent or a notification) and wait for the shell to show them.
 * :app creates one instance, offers keys from its activity, and the shell collects them once the user is signed
 * in. Keys are kept until collected, so a cold start from a notification is not lost while the splash runs.
 */
class PendingNavigation {
    private val channel = Channel<NavKey>(Channel.UNLIMITED)

    /** Keys in arrival order. Collect from one place only; a key is delivered once. */
    val keys: Flow<NavKey> = channel.receiveAsFlow()

    /** Queues [key]. Never blocks and never fails, because the channel is unlimited. */
    fun offer(key: NavKey) {
        channel.trySend(key)
    }
}
