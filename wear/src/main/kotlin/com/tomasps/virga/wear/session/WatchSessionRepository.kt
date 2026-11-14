package com.tomasps.virga.wear.session

import com.tomasps.virga.core.auth.Credential
import com.tomasps.virga.core.auth.session.ActiveCredential
import com.tomasps.virga.core.auth.vault.CredentialVault
import com.tomasps.virga.core.network.demo.DemoMode
import com.tomasps.virga.core.wearsync.HandoffCodec
import com.tomasps.virga.core.wearsync.HandoffDecode
import com.tomasps.virga.core.wearsync.HandoffPayload
import com.tomasps.virga.core.wearsync.StalenessPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The watch's view of the phone's credential handoff. [clock] is the watch's own clock: the check-in time is taken
 * from it, never from the payload, so clock skew between the devices cannot defeat the 24-hour rule.
 * Mutations are serialized because the listener service, the UI and the API layer all call in.
 */
class WatchSessionRepository(
    private val vault: CredentialVault,
    private val active: ActiveCredential,
    private val demoMode: DemoMode,
    private val prefs: WatchSessionPrefs,
    private val clock: () -> Long,
) {
    private val lock = Mutex()
    private val mutableState = MutableStateFlow<WatchSessionState>(WatchSessionState.Restoring)
    val state: StateFlow<WatchSessionState> = mutableState.asStateFlow()

    suspend fun restore() = lock.withLock {
        val credential = vault.read()
        val lastCheckIn = prefs.lastCheckInMillis()
        when {
            credential == null || credential == Credential.OAuthReserved -> deactivate(WatchSessionState.NotConnected)
            StalenessPolicy.isStale(lastCheckIn, clock()) -> deactivate(WatchSessionState.Stale)
            else -> activate(credential, prefs.selectedAccountId(), prefs.accountIds())
        }
    }

    suspend fun apply(payload: HandoffPayload) = lock.withLock {
        val credential = credentialOf(payload)
        if (credential == null) {
            wipe()
            return@withLock
        }
        if (!vault.write(credential)) return@withLock
        val accountIds = payload.accounts.map { it.id }.toSet()
        prefs.write(clock(), payload.selectedAccountId, accountIds)
        activate(credential, payload.selectedAccountId, accountIds)
    }

    suspend fun clear() = lock.withLock { wipe() }

    /** The API rejected the token: it is revoked or expired, so keep nothing. */
    suspend fun onUnauthorized() = lock.withLock { wipe() }

    /** Moves a connected session to [WatchSessionState.Stale] once the 24-hour window has passed. */
    suspend fun refreshStaleness() = lock.withLock {
        if (mutableState.value !is WatchSessionState.Connected) return@withLock
        if (StalenessPolicy.isStale(prefs.lastCheckInMillis(), clock())) deactivate(WatchSessionState.Stale)
    }

    private fun credentialOf(payload: HandoffPayload): Credential? {
        val token = payload.token
        return when {
            payload.isDemo -> Credential.Demo
            token != null -> Credential.ApiToken(token)
            else -> null
        }
    }

    /** Connects only when the selection names an account in the list, so a stale id never reaches the API. */
    private fun activate(credential: Credential, selectedAccountId: String?, accountIds: Set<String>) {
        if (selectedAccountId == null || selectedAccountId !in accountIds) {
            deactivate(WatchSessionState.NotConnected)
            return
        }
        val isDemo = credential == Credential.Demo
        active.use((credential as? Credential.ApiToken)?.token)
        demoMode.setEnabled(isDemo)
        mutableState.value = WatchSessionState.Connected(selectedAccountId, isDemo)
    }

    private fun deactivate(next: WatchSessionState) {
        active.use(null)
        demoMode.setEnabled(false)
        mutableState.value = next
    }

    private suspend fun wipe() {
        try {
            vault.wipe()
            prefs.clear()
        } finally {
            deactivate(WatchSessionState.NotConnected)
        }
    }
}

/**
 * Decodes a Data Layer item body and applies it. An undecodable or too-new payload changes nothing and returns false,
 * so a bad item can never disconnect a working watch.
 */
suspend fun WatchSessionRepository.applyEncoded(bytes: ByteArray): Boolean =
    when (val decoded = HandoffCodec.decode(bytes)) {
        is HandoffDecode.Ok -> {
            apply(decoded.payload)
            true
        }
        HandoffDecode.Invalid -> false
    }
