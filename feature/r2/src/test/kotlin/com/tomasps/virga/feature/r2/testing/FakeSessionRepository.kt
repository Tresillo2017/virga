package com.tomasps.virga.feature.r2.testing

import com.tomasps.virga.core.auth.session.CloudflareAccount
import com.tomasps.virga.core.auth.session.SessionRepository
import com.tomasps.virga.core.auth.session.SessionState
import com.tomasps.virga.core.common.result.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Signed-in session for one account, with a switch for tests that need a signed-out start. */
class FakeSessionRepository(accountId: String? = ACCOUNT_ID) : SessionRepository {
    private val mutableState = MutableStateFlow(signedIn(accountId))
    override val state: StateFlow<SessionState> = mutableState.asStateFlow()

    fun select(accountId: String?) {
        mutableState.value = signedIn(accountId)
    }

    override suspend fun restore() = Unit
    override suspend fun signInWithToken(token: String): ApiResult<Unit> = ApiResult.Success(Unit)
    override suspend fun enterDemo(): ApiResult<Unit> = ApiResult.Success(Unit)
    override suspend fun selectAccount(accountId: String) = Unit
    override suspend fun signOut() {
        mutableState.value = SessionState.SignedOut
    }

    companion object {
        const val ACCOUNT_ID = "acc-1"

        private fun signedIn(accountId: String?): SessionState {
            val accounts = accountId?.let { listOf(CloudflareAccount(id = it, name = "Test")) }.orEmpty()
            return SessionState.SignedIn(accounts = accounts, selectedAccountId = accountId, isDemo = false)
        }
    }
}
