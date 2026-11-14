package com.tomasps.virga.wearsync

import com.google.android.gms.common.api.ApiException
import com.tomasps.virga.core.auth.Credential
import com.tomasps.virga.core.auth.session.SessionRepository
import com.tomasps.virga.core.auth.session.SessionState
import com.tomasps.virga.core.auth.vault.CredentialVault
import com.tomasps.virga.core.wearsync.HandoffAccount
import com.tomasps.virga.core.wearsync.HandoffPayload
import com.tomasps.virga.core.wearsync.HandoffTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** How long a heartbeat waits for a cold-started session to leave Restoring (restore loads accounts, up to 8s). */
const val SETTLE_TIMEOUT_MILLIS = 15_000L

/** Attempts the clear-retry worker makes before giving up; the flag stays set for the next cold start. */
const val MAX_CLEAR_ATTEMPTS = 5

/** Whether a paired watch has the Virga watch app; [Unknown] when the lookup failed for a transient reason. */
private enum class WatchPresence { Present, Absent, Unknown }

/**
 * Mirrors the signed-in credential to the paired watch. Publishes on every [SessionState.SignedIn] change, clears the
 * watch on [SessionState.SignedOut] and ignores [SessionState.Restoring]. Demo publishes no token, only the flag.
 *
 * The token reaches the Data Layer only while [watchApp] confirms a watch with the Virga app. Signed in with no such
 * watch, any earlier item is deleted (the same clear as sign-out) and the [heartbeat] is cancelled; it is scheduled
 * only alongside a publish. A lookup that fails transiently neither publishes nor clears, so a working watch is never
 * wiped on a guess. Sign-out clears without asking.
 *
 * Nothing here throws for a user without a watch. A user who never published has nothing to clear, so the clear path
 * is skipped entirely. A failed clear is remembered in [pendingClear] and retried by [retryPendingClear] (a bounded
 * WorkManager job) and by the next cold start, when the collector re-runs the sign-out clear. A permanently
 * unavailable Wearable API ([isUnavailable]) is terminal: nothing is retried or logged. Other failures are logged
 * through [log] by exception class and status code only, never a message or the payload.
 */
class HandoffPublisher(
    private val session: SessionRepository,
    private val vault: CredentialVault,
    private val transport: HandoffTransport,
    private val clock: () -> Long,
    private val pendingClear: PendingClearStore,
    private val watchApp: WatchAppFinder,
    private val heartbeat: HeartbeatScheduler,
    private val scheduleClearRetry: () -> Unit = {},
    private val log: (String) -> Unit = {},
    private val isUnavailable: (Exception) -> Boolean = { false },
) {
    // Serialises the collector, the heartbeat and the retry so an older action cannot land after a newer one.
    private val gate = Mutex()

    fun start(scope: CoroutineScope) {
        scope.launch {
            // The state is re-read under the gate, so a conflated or stale emission is never acted on.
            session.state.collectLatest { sync() }
        }
    }

    /**
     * Republishes the current state with a fresh timestamp. Waits (bounded) for a cold-started session to settle.
     * Returns false only when it never settled, so a worker retries. A failed publish still returns true: the next
     * period tries again.
     */
    /** The set of watches with the Virga app changed: publish or clear to match. Same contract as [heartbeat]. */
    suspend fun reevaluate(): Boolean = heartbeat()

    suspend fun heartbeat(): Boolean {
        if (!awaitSettled()) return false
        sync()
        return true
    }

    /**
     * Retries a clear that failed earlier. [attempt] is the worker's zero-based run count. Returns true when the work
     * is finished (cleared, nothing pending, unavailable API or attempts used up; the flag then stays set for the next
     * cold start), false when it should be retried.
     */
    suspend fun retryPendingClear(attempt: Int): Boolean {
        if (!awaitSettled()) return attempt + 1 >= MAX_CLEAR_ATTEMPTS
        val done = sync(onlyPendingClear = true)
        return done || attempt + 1 >= MAX_CLEAR_ATTEMPTS
    }

    private suspend fun awaitSettled(): Boolean =
        withTimeoutOrNull(SETTLE_TIMEOUT_MILLIS) { session.state.first { it !is SessionState.Restoring } } != null

    private suspend fun sync(onlyPendingClear: Boolean = false): Boolean = gate.withLock {
        when (val state = session.state.value) {
            SessionState.Restoring -> true
            SessionState.SignedOut -> clearThenStopHeartbeat(force = onlyPendingClear)
            is SessionState.SignedIn -> syncSignedIn(state, onlyPendingClear)
        }
    }

    private suspend fun syncSignedIn(state: SessionState.SignedIn, onlyPendingClear: Boolean): Boolean =
        when (watchPresence()) {
            WatchPresence.Unknown -> true
            WatchPresence.Absent -> clearThenStopHeartbeat(force = onlyPendingClear)
            // Signed in again: the next publish resets a pending clear once it actually lands.
            WatchPresence.Present -> if (onlyPendingClear) {
                true
            } else {
                heartbeat.schedule()
                publish(state)
            }
        }

    /**
     * Clears first: when this runs inside the heartbeat worker, cancelling the heartbeat cancels that running worker,
     * which would otherwise abort the clear mid-flight and leave the item in Play services storage.
     */
    private suspend fun clearThenStopHeartbeat(force: Boolean): Boolean {
        val done = clearWatch(force)
        heartbeat.cancel()
        return done
    }

    private suspend fun watchPresence(): WatchPresence = try {
        if (watchApp.hasWatchApp()) WatchPresence.Present else WatchPresence.Absent
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        // No Wearable API on this phone means no watch can have the app.
        if (isUnavailable(error)) {
            WatchPresence.Absent
        } else {
            log("Wear capability lookup failed: ${describe(error)}")
            WatchPresence.Unknown
        }
    }

    private suspend fun clearWatch(force: Boolean): Boolean {
        if (force && !pendingClear.isSet()) return true
        val everPublished = pendingClear.hasEverPublished()
        // Nothing was ever sent to a watch and no clear is owed, so there is nothing to clear.
        if (!everPublished && !pendingClear.isSet()) return true
        pendingClear.set(true)
        return try {
            transport.clear()
            pendingClear.set(false)
            true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (isUnavailable(error)) {
                // No Wearable API right now: stop retrying in the background. If something was ever published the
                // flag stays set so a later cold start still sends the delete; otherwise there is nothing to owe.
                if (!everPublished) pendingClear.set(false)
                return true
            }
            log("Wear clear failed: ${describe(error)}")
            scheduleClearRetry()
            false
        }
    }

    private suspend fun publish(state: SessionState.SignedIn): Boolean {
        // A demo-flagged state never carries a token, whatever the vault holds.
        val token = when (val credential = vault.read()) {
            is Credential.ApiToken -> if (state.isDemo) null else credential.token
            Credential.Demo -> null
            // Unreadable or unsupported vault: there is nothing safe to send.
            Credential.OAuthReserved, null -> return true
        }
        if (token == null && !state.isDemo) return true
        // Sign-out may have happened while the vault was read; do not publish for a signed-out session.
        if (session.state.value !is SessionState.SignedIn) return true
        val payload = HandoffPayload(
            version = HandoffPayload.CURRENT_VERSION,
            token = token,
            isDemo = state.isDemo,
            accounts = state.accounts.map { HandoffAccount(it.id, it.name) },
            selectedAccountId = state.selectedAccountId,
            sentAtMillis = clock(),
        )
        // Recorded before the put: once the request is handed to the Data Layer it may land even if this sync is
        // cancelled or the process dies, and a later sign-out must still know to clear.
        pendingClear.markPublished()
        return try {
            transport.publish(payload)
            // A newer credential now sits on the watch, so an older pending clear must not wipe it.
            pendingClear.set(false)
            true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (!isUnavailable(error)) log("Wear publish failed: ${describe(error)}")
            true
        }
    }

    private fun describe(error: Exception): String {
        val status = (error as? ApiException)?.statusCode
        return error::class.java.name + (status?.let { " status=$it" } ?: "")
    }
}
