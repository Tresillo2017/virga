package com.tomasps.virga.core.auth.session

import com.tomasps.virga.core.auth.Credential
import com.tomasps.virga.core.auth.vault.CredentialVault
import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.CloudflareApi
import com.tomasps.virga.core.network.allPages
import com.tomasps.virga.core.network.demo.DemoMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

private const val VERIFY_PATH = "/user/tokens/verify"
private const val ACCOUNTS_PATH = "/accounts"
private const val ACTIVE_STATUS = "active"
private const val RESTORE_TIMEOUT_MS = 8_000L

@Serializable
private data class TokenVerification(val id: String? = null, val status: String? = null)

private val accountListSerializer = ListSerializer(CloudflareAccount.serializer())

class DefaultSessionRepository(
    private val api: CloudflareApi,
    private val active: ActiveCredential,
    private val demoMode: DemoMode,
    private val vault: CredentialVault,
    private val prefs: SessionPreferences,
) : SessionRepository {

    private val mutableState = MutableStateFlow<SessionState>(SessionState.Restoring)
    override val state: StateFlow<SessionState> = mutableState.asStateFlow()

    /** Any failure on this path settles the session as signed out, so the splash always clears. */
    override suspend fun restore() {
        try {
            restoreStored()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            settleSignedOut()
        }
    }

    override suspend fun signInWithToken(token: String): ApiResult<Unit> {
        val previousToken = active.bearerToken()
        val wasDemo = demoMode.enabled.value
        demoMode.setEnabled(false)
        active.use(token)
        val verified = verifyToken()
        if (verified is ApiResult.Failure) return revertSignIn(previousToken, wasDemo, verified)
        val loaded = loadAccounts()
        if (loaded is ApiResult.Failure) return revertSignIn(previousToken, wasDemo, loaded)
        if (persistLive(token, (loaded as ApiResult.Success).value)) return ApiResult.Success(Unit)
        return revertSignIn(previousToken, wasDemo, ApiResult.Failure(ApiError.Local(SAVE_FAILED_REASON)))
    }

    override suspend fun enterDemo(): ApiResult<Unit> {
        active.use(null)
        demoMode.setEnabled(true)
        return when (val loaded = loadAccounts()) {
            is ApiResult.Failure -> {
                demoMode.setEnabled(false)
                loaded
            }
            is ApiResult.Success -> enterDemoWith(loaded.value)
        }
    }

    override suspend fun selectAccount(accountId: String) {
        val current = mutableState.value as? SessionState.SignedIn ?: return
        if (current.accounts.none { it.id == accountId }) return
        if (!current.isDemo) prefs.writeSelectedAccountId(accountId)
        mutableState.value = current.copy(selectedAccountId = accountId)
    }

    /** Wipes storage first, then always settles as signed out, even if a wipe step throws. */
    override suspend fun signOut() {
        try {
            vault.wipe()
            prefs.clear()
        } finally {
            active.use(null)
            demoMode.setEnabled(false)
            mutableState.value = SessionState.SignedOut
        }
    }

    private suspend fun restoreStored() {
        when (val stored = vault.read()) {
            is Credential.ApiToken -> restoreLive(stored.token)
            Credential.Demo -> restoreDemo()
            Credential.OAuthReserved, null -> settleSignedOut()
        }
    }

    private suspend fun revertSignIn(
        previousToken: String?,
        wasDemo: Boolean,
        failure: ApiResult<Nothing>,
    ): ApiResult<Unit> {
        active.use(previousToken)
        demoMode.setEnabled(wasDemo)
        return failure
    }

    /**
     * Stores the verified token and publishes the accounts. Returns false, after removing any token that was
     * written, when either step fails. Storage errors become a Failure; they are not rethrown.
     */
    private suspend fun persistLive(token: String, accounts: List<CloudflareAccount>): Boolean = try {
        val written = vault.write(Credential.ApiToken(token))
        if (written) publishLive(accounts)
        written
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        wipeVaultQuietly()
        false
    }

    /** Removes a token that was written before a later step failed. A failure here leaves the old state. */
    private suspend fun wipeVaultQuietly() {
        try {
            vault.wipe()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // Intentionally ignored: the sign-in has already failed and reported its error.
        }
    }

    private suspend fun enterDemoWith(accounts: List<CloudflareAccount>): ApiResult<Unit> {
        if (!vault.write(Credential.Demo)) {
            demoMode.setEnabled(false)
            return ApiResult.Failure(ApiError.Local(SAVE_FAILED_REASON))
        }
        publish(accounts, storedSelection = null, isDemo = true)
        return ApiResult.Success(Unit)
    }

    private suspend fun restoreLive(token: String) {
        active.use(token)
        val fresh = withTimeoutOrNull(RESTORE_TIMEOUT_MS) { loadAccounts() }
        when (fresh) {
            is ApiResult.Success -> publishLive(fresh.value)
            is ApiResult.Failure -> if (fresh.error is ApiError.Unauthorized) signOut() else useCachedAccounts()
            null -> useCachedAccounts()
        }
    }

    private suspend fun restoreDemo() {
        demoMode.setEnabled(true)
        when (val loaded = loadAccounts()) {
            is ApiResult.Success -> publish(loaded.value, storedSelection = null, isDemo = true)
            is ApiResult.Failure -> {
                demoMode.setEnabled(false)
                mutableState.value = SessionState.SignedOut
            }
        }
    }

    private suspend fun useCachedAccounts() {
        publishLive(prefs.readAccounts(), persist = false)
    }

    /**
     * The state a failed or absent credential leaves behind: signed out, demo off, and the account cache
     * cleared. The cache holds names and ids only, and the next sign-in rewrites it, so a failure to clear it is
     * not allowed to keep the session from settling. The vault is left alone here: a failed read may be
     * transient, and a successful sign-in overwrites the slot anyway.
     */
    private suspend fun settleSignedOut() {
        demoMode.setEnabled(false)
        mutableState.value = SessionState.SignedOut
        try {
            prefs.clear()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // Intentionally ignored: see the KDoc above.
        }
    }

    private suspend fun publishLive(accounts: List<CloudflareAccount>, persist: Boolean = true) {
        val storedSelection = prefs.readSelectedAccountId()
        publish(accounts, storedSelection, isDemo = false, persist = persist)
    }

    private suspend fun publish(
        accounts: List<CloudflareAccount>,
        storedSelection: String?,
        isDemo: Boolean,
        persist: Boolean = !isDemo,
    ) {
        val selected = accounts.firstOrNull { it.id == storedSelection }?.id ?: accounts.firstOrNull()?.id
        if (persist) {
            prefs.writeAccounts(accounts)
            prefs.writeSelectedAccountId(selected)
        }
        mutableState.value = SessionState.SignedIn(accounts, selected, isDemo)
    }

    private suspend fun verifyToken(): ApiResult<TokenVerification> =
        when (val result = api.get<TokenVerification>(VERIFY_PATH)) {
            is ApiResult.Failure -> result
            is ApiResult.Success ->
                if (result.value.status == ACTIVE_STATUS) result else ApiResult.Failure(ApiError.Unauthorized())
        }

    private suspend fun loadAccounts(): ApiResult<List<CloudflareAccount>> =
        api.allPages(ACCOUNTS_PATH, deserializer = accountListSerializer)

    private companion object {
        const val SAVE_FAILED_REASON = "Credential or account cache could not be saved"
    }
}
