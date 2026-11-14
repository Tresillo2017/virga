package com.tomasps.virga.wearsync

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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Mirrors the signed-in credential to the paired watch. Publishes on every [SessionState.SignedIn] change, clears the
 * watch on [SessionState.SignedOut] and ignores [SessionState.Restoring]. Demo publishes no token, only the flag.
 *
 * Nothing here throws for a user without a watch: a transport failure is swallowed, and the message is never logged
 * because the payload carries the token.
 */
class HandoffPublisher(
    private val session: SessionRepository,
    private val vault: CredentialVault,
    private val transport: HandoffTransport,
    private val clock: () -> Long,
) {
    // Serialises the collector and the heartbeat so an older publish cannot land after a newer one.
    private val gate = Mutex()

    fun start(scope: CoroutineScope) {
        scope.launch {
            session.state.collectLatest { state -> sync(state) }
        }
    }

    /** Republishes the current state with a fresh timestamp so the watch does not treat the handoff as stale. */
    suspend fun heartbeat() = sync(session.state.value)

    private suspend fun sync(state: SessionState) = gate.withLock {
        when (state) {
            SessionState.Restoring -> Unit
            SessionState.SignedOut -> guarded { transport.clear() }
            is SessionState.SignedIn -> publish(state)
        }
    }

    private suspend fun publish(state: SessionState.SignedIn) {
        val token = when (val credential = vault.read()) {
            is Credential.ApiToken -> credential.token
            Credential.Demo -> null
            // Unreadable or unsupported vault: there is nothing safe to send.
            Credential.OAuthReserved, null -> return
        }
        if (token == null && !state.isDemo) return
        val payload = HandoffPayload(
            version = HandoffPayload.CURRENT_VERSION,
            token = token,
            isDemo = state.isDemo,
            accounts = state.accounts.map { HandoffAccount(it.id, it.name) },
            selectedAccountId = state.selectedAccountId,
            sentAtMillis = clock(),
        )
        guarded { transport.publish(payload) }
    }

    private suspend fun guarded(block: suspend () -> Unit) {
        try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // No watch, no Play Services or a Data Layer error. The next change or heartbeat retries.
        }
    }
}
