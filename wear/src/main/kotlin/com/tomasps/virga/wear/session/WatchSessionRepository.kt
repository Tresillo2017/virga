package com.tomasps.virga.wear.session

import com.tomasps.virga.core.auth.Credential
import com.tomasps.virga.core.auth.session.ActiveCredential
import com.tomasps.virga.core.auth.vault.CredentialVault
import com.tomasps.virga.core.network.demo.DemoMode
import com.tomasps.virga.core.wearsync.HandoffCodec
import com.tomasps.virga.core.wearsync.HandoffDecode
import com.tomasps.virga.core.wearsync.HandoffPayload
import com.tomasps.virga.core.wearsync.StalenessPolicy
import kotlinx.coroutines.CancellationException
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

    /** The watch-clock time of the last accepted handoff, held in memory so use-time checks need no I/O. */
    @Volatile
    private var checkInMillis: Long? = null

    /** The token in use with its generation, as one value so a reader never pairs a token with another's generation. */
    @Volatile
    private var issued: IssuedCredential? = null
    private var lastGeneration = 0L

    fun isCheckInStale(): Boolean = StalenessPolicy.isStale(checkInMillis, clock())

    /** The credential requests may use right now, or null. Staleness is the provider's check, not this one's. */
    fun issuedCredential(): IssuedCredential? = issued

    /** Never throws except on cancellation: a failed restore settles as [WatchSessionState.NotConnected]. */
    suspend fun restore() = lock.withLock {
        try {
            restoreStored()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            deactivate(WatchSessionState.NotConnected)
        }
    }

    /**
     * A payload with no token that is not demo is treated as [clear]. Metadata is written in a crash-safe order: the
     * check-in is cleared first on disk, then the vault, then the metadata, so a failure part-way restores as Stale or
     * NotConnected and never as Connected with a mix of old and new data. A storage failure disconnects and rethrows.
     * The in-memory session keeps serving the previous credential until the new one is committed, so requests that
     * overlap a slow write neither stall on the lock nor fail locally.
     */
    suspend fun apply(payload: HandoffPayload) = lock.withLock {
        val credential = credentialOf(payload)
        if (credential == null) {
            wipe()
            return@withLock
        }
        try {
            prefs.clearCheckIn()
            check(vault.write(credential)) { "vault write failed" }
            val accountIds = payload.accounts.map { it.id }.toSet()
            val checkIn = clock()
            prefs.write(checkIn, payload.selectedAccountId, accountIds)
            checkInMillis = checkIn
            activate(credential, payload.selectedAccountId, accountIds)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            checkInMillis = null
            deactivate(WatchSessionState.NotConnected)
            throw error
        }
    }

    suspend fun clear() = lock.withLock { wipe() }

    /**
     * The API rejected the credential of [sentGeneration]: it is revoked or expired, so keep nothing. A rejection of a
     * credential that has since been replaced or cleared changes nothing, so it cannot wipe a newer session.
     */
    suspend fun onUnauthorized(sentGeneration: Long) = lock.withLock {
        if (issued?.generation == sentGeneration) wipe()
    }

    /** Moves a connected session to [WatchSessionState.Stale] once the 24-hour window has passed. */
    suspend fun refreshStaleness() = lock.withLock {
        if (mutableState.value !is WatchSessionState.Connected) return@withLock
        if (isCheckInStale()) deactivate(WatchSessionState.Stale)
    }

    private suspend fun restoreStored() {
        val credential = vault.read()
        val lastCheckIn = prefs.lastCheckInMillis()
        checkInMillis = lastCheckIn
        when {
            credential == null || credential == Credential.OAuthReserved -> deactivate(WatchSessionState.NotConnected)
            StalenessPolicy.isStale(lastCheckIn, clock()) -> deactivate(WatchSessionState.Stale)
            else -> activate(credential, prefs.selectedAccountId(), prefs.accountIds())
        }
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
        useToken((credential as? Credential.ApiToken)?.token)
        demoMode.setEnabled(isDemo)
        mutableState.value = WatchSessionState.Connected(selectedAccountId, isDemo)
    }

    private fun deactivate(next: WatchSessionState) {
        useToken(null)
        demoMode.setEnabled(false)
        mutableState.value = next
    }

    /** A new generation only when the token itself changes, so re-applying the same token keeps its generation. */
    private fun useToken(token: String?) {
        if (token != issued?.token) issued = token?.let { IssuedCredential(it, ++lastGeneration) }
        active.use(token)
    }

    /**
     * Prefs go first so a vault wipe that fails still leaves a restart with no check-in (Stale or NotConnected, never
     * Connected). Both steps are always attempted and the session is always disconnected; the first failure is rethrown.
     */
    private suspend fun wipe() {
        checkInMillis = null
        deactivate(WatchSessionState.NotConnected)
        val prefsFailure = attempt { prefs.clear() }
        val vaultFailure = attempt { vault.wipe() }
        (prefsFailure ?: vaultFailure)?.let { throw it }
    }

    private suspend fun attempt(block: suspend () -> Unit): Exception? = try {
        block()
        null
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        error
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
