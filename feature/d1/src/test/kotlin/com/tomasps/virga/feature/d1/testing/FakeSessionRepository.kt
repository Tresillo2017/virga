package com.tomasps.virga.feature.d1.testing

import com.tomasps.virga.core.auth.session.SessionRepository
import com.tomasps.virga.core.auth.session.SessionState
import com.tomasps.virga.core.common.result.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-memory session with recorded calls. Results are configurable per test. */
class FakeSessionRepository(initial: SessionState = SessionState.SignedOut) : SessionRepository {
    private val mutableState = MutableStateFlow(initial)
    override val state: StateFlow<SessionState> = mutableState.asStateFlow()

    var signInResult: ApiResult<Unit> = ApiResult.Success(Unit)
    var demoResult: ApiResult<Unit> = ApiResult.Success(Unit)
    val signInTokens = mutableListOf<String>()
    val selectedAccountIds = mutableListOf<String>()
    var demoEntries = 0
    var signOuts = 0

    fun emit(next: SessionState) {
        mutableState.value = next
    }

    override suspend fun restore() = Unit

    override suspend fun signInWithToken(token: String): ApiResult<Unit> {
        signInTokens += token
        return signInResult
    }

    override suspend fun enterDemo(): ApiResult<Unit> {
        demoEntries++
        return demoResult
    }

    override suspend fun selectAccount(accountId: String) {
        selectedAccountIds += accountId
    }

    override suspend fun signOut() {
        signOuts++
        mutableState.value = SessionState.SignedOut
    }
}
