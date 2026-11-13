package com.tomasps.virga.feature.workers.testing

import com.tomasps.virga.core.auth.session.SessionRepository
import com.tomasps.virga.core.auth.session.SessionState
import com.tomasps.virga.core.common.result.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-memory session. Tests move it between accounts with [signedInAs]. */
class FakeSessionRepository(initial: SessionState = SessionState.SignedOut) : SessionRepository {
    private val mutableState = MutableStateFlow(initial)
    override val state: StateFlow<SessionState> = mutableState.asStateFlow()

    fun signedInAs(accountId: String?) {
        mutableState.value = SessionState.SignedIn(
            accounts = listOf(com.tomasps.virga.core.auth.session.CloudflareAccount(id = "acc-1"), com.tomasps.virga.core.auth.session.CloudflareAccount(id = "acc-2")),
            selectedAccountId = accountId,
            isDemo = false,
        )
    }

    override suspend fun restore() = Unit

    override suspend fun signInWithToken(token: String): ApiResult<Unit> = ApiResult.Success(Unit)

    override suspend fun enterDemo(): ApiResult<Unit> = ApiResult.Success(Unit)

    override suspend fun selectAccount(accountId: String) = Unit

    override suspend fun signOut() {
        mutableState.value = SessionState.SignedOut
    }
}
