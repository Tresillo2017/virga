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

/**
 * Mirrors the signed-in credential to the paired watch. Publishes on every [SessionState.SignedIn] change, clears the
 * watch on [SessionState.SignedOut] and ignores [SessionState.Restoring]. Demo publishes no token, only the flag.
 *
 * Nothing here throws for a user without a watch. A failed clear is remembered in [pendingClear] and retried by
 * [retryPendingClear] (a WorkManager job and every app start), so a signed-out phone cannot leave a token on the
 * watch. Failures are logged through [log] by exception class and status code only, never a message or the payload.
 */
class HandoffPublisher(
    private val session: SessionRepository,
    private val vault: CredentialVault,
    private val transport: HandoffTransport,
    private val clock: () -> Long,
    private val pendingClear: PendingClearStore,
    private val scheduleClearRetry: () -> Unit = {},
    private val log: (String) -> Unit = {},
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
     * Returns false when it never settled or the publish failed, so a worker can retry.
     */
    suspend fun heartbeat(): Boolean {
        if (!awaitSettled()) return false
        return sync()
    }

    /** Retries a clear that failed earlier. Returns true when nothing is left to do, false when it should be retried. */
    suspend fun retryPendingClear(): Boolean {
        if (!awaitSettled()) return false
        return sync(onlyPendingClear = true)
    }

    private suspend fun awaitSettled(): Boolean =
        withTimeoutOrNull(SETTLE_TIMEOUT_MILLIS) { session.state.first { it !is SessionState.Restoring } } != null

    private suspend fun sync(onlyPendingClear: Boolean = false): Boolean = gate.withLock {
        when (val state = session.state.value) {
            SessionState.Restoring -> true
            SessionState.SignedOut -> clearWatch(force = onlyPendingClear)
            is SessionState.SignedIn -> when {
                onlyPendingClear -> pendingClear.set(false).let { true }
                else -> publish(state)
            }
        }
    }

    private suspend fun clearWatch(force: Boolean): Boolean {
        if (force && !pendingClear.isSet()) return true
        pendingClear.set(true)
        return try {
            transport.clear()
            pendingClear.set(false)
            true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
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
        // Sign-out or a switch may have happened while the vault was read; do not publish for a stale session.
        if (session.state.value !is SessionState.SignedIn) return true
        val payload = HandoffPayload(
            version = HandoffPayload.CURRENT_VERSION,
            token = token,
            isDemo = state.isDemo,
            accounts = state.accounts.map { HandoffAccount(it.id, it.name) },
            selectedAccountId = state.selectedAccountId,
            sentAtMillis = clock(),
        )
        return try {
            transport.publish(payload)
            // A newer credential now sits on the watch, so an older pending clear must not wipe it.
            pendingClear.set(false)
            true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            log("Wear publish failed: ${describe(error)}")
            false
        }
    }

    private fun describe(error: Exception): String {
        val status = (error as? ApiException)?.statusCode
        return error::class.java.name + (status?.let { " status=$it" } ?: "")
    }
}
