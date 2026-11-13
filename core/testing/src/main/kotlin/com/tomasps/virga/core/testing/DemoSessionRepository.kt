package com.tomasps.virga.core.testing

import com.tomasps.virga.core.auth.session.CloudflareAccount
import com.tomasps.virga.core.auth.session.SessionRepository
import com.tomasps.virga.core.auth.session.SessionState
import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The accounts the built-in demo account offers. The ids match the demo routes in each feature. */
val DEFAULT_DEMO_ACCOUNTS: List<CloudflareAccount> = listOf(
    CloudflareAccount(id = "demo-account-personal", name = "Personal (demo)"),
    CloudflareAccount(id = "demo-account-staging", name = "Staging (demo)"),
)

/**
 * A demo session that is already signed in, with [selectedAccountId] selected. It replaces the real session layer,
 * because that layer needs the Android Keystore and DataStore, which a JVM test does not have. Features read only
 * [state], so the replacement is exact for them. Sign-in is refused, because the harness never runs live.
 */
class DemoSessionRepository(
    accounts: List<CloudflareAccount> = DEFAULT_DEMO_ACCOUNTS,
    selectedAccountId: String = accounts.first().id,
) : SessionRepository {

    private val mutableState = MutableStateFlow<SessionState>(
        SessionState.SignedIn(accounts = accounts, selectedAccountId = selectedAccountId, isDemo = true),
    )
    override val state: StateFlow<SessionState> = mutableState.asStateFlow()

    override suspend fun restore() = Unit

    override suspend fun signInWithToken(token: String): ApiResult<Unit> =
        ApiResult.Failure(ApiError.Local(LIVE_SIGN_IN_REFUSED))

    override suspend fun enterDemo(): ApiResult<Unit> = ApiResult.Success(Unit)

    override suspend fun selectAccount(accountId: String) {
        val signedIn = mutableState.value as? SessionState.SignedIn ?: return
        if (signedIn.accounts.none { it.id == accountId }) return
        mutableState.value = signedIn.copy(selectedAccountId = accountId)
    }

    override suspend fun signOut() {
        mutableState.value = SessionState.SignedOut
    }

    private companion object {
        const val LIVE_SIGN_IN_REFUSED = "Live sign-in is not available in the UI flow harness"
    }
}
