package com.tomasps.virga.feature.pages.testing

import com.tomasps.virga.core.auth.session.SessionRepository
import com.tomasps.virga.core.auth.session.SessionState
import com.tomasps.virga.core.common.result.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-memory session for ViewModel tests. [emit] simulates an account switch. */
class FakeSessionRepository(initial: SessionState) : SessionRepository {
    private val mutableState = MutableStateFlow(initial)
    override val state: StateFlow<SessionState> = mutableState.asStateFlow()

    fun emit(next: SessionState) {
        mutableState.value = next
    }

    override suspend fun restore() = Unit

    override suspend fun signInWithToken(token: String): ApiResult<Unit> = ApiResult.Success(Unit)

    override suspend fun enterDemo(): ApiResult<Unit> = ApiResult.Success(Unit)

    override suspend fun selectAccount(accountId: String) = Unit

    override suspend fun signOut() {
        mutableState.value = SessionState.SignedOut
    }
}
