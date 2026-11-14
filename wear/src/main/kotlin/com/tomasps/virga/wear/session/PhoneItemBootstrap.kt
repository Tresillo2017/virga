package com.tomasps.virga.wear.session

import android.util.Log
import kotlinx.coroutines.CancellationException

private const val TAG = "VirgaWear"

/** The phone's current credential item body, or null when there is none. Throws when the Data Layer read fails. */
fun interface CredentialItemSource {
    suspend fun current(): ByteArray?
}

/**
 * Picks up a credential item that was published before this watch app could receive the change event: installed
 * after the phone signed in, or reset. Runs at startup and on app open, only while NotConnected or Stale, and makes no
 * network call. The repository applies the item only if it is not the one last applied and nothing changed the
 * session meanwhile, so a Stale lock, a Disconnect or a 401 wipe is never undone by re-reading the same old item.
 */
class PhoneItemBootstrap(
    private val session: WatchSessionRepository,
    private val source: CredentialItemSource,
    private val warn: (String) -> Unit = { Log.w(TAG, it) },
) {
    suspend fun syncFromPhone() {
        val since = session.changeCount()
        val state = session.state.value
        if (state != WatchSessionState.NotConnected && state != WatchSessionState.Stale) return
        try {
            val bytes = source.current() ?: return
            session.applyStoredItem(bytes, since)
        } catch (error: CancellationException) {
            throw error
        } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
            // Class name only: a message could carry storage paths or credential material.
            warn("Reading the phone's credential item failed: ${error.javaClass.simpleName}")
        }
    }
}
