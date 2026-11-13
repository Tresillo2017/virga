package com.tomasps.virga.core.auth.session

import com.tomasps.virga.core.common.result.ApiResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

/** The app's single source of truth for who is signed in. Features read the selected account from here. */
interface SessionRepository {
    val state: StateFlow<SessionState>

    /**
     * Cold start: reads the vault and restores the session. Leaves [SessionState.Restoring] in every case,
     * including when the stored data is unreadable (then the result is [SessionState.SignedOut]).
     */
    suspend fun restore()

    /**
     * Verifies [token] (GET /user/tokens/verify), then loads accounts. Demo mode is switched off first so the
     * check always reaches the live API. Nothing is saved on failure, and the previous token and demo flag are
     * restored.
     */
    suspend fun signInWithToken(token: String): ApiResult<Unit>

    /**
     * Turns on demo mode with the demo accounts. It stores the demo credential in the one vault slot, which
     * replaces any stored live token. Sign-in is only offered while signed out, so no live token is lost in
     * practice.
     */
    suspend fun enterDemo(): ApiResult<Unit>

    /** Persists the selection for the live account. Ignored for an unknown id. */
    suspend fun selectAccount(accountId: String)

    /** Wipes the vault and the account cache, clears the token and leaves demo mode. */
    suspend fun signOut()
}

/**
 * The selected account id as a flow for ViewModels. Emits each new id once, skips the signed-out and restoring
 * states, and emits again after a sign-out and back in. Use with `collectLatest` so a switch cancels the load
 * for the previous account:
 *
 * ```
 * viewModelScope.launch { session.selectedAccountIds().collectLatest { loadFor(it) } }
 * ```
 */
fun SessionRepository.selectedAccountIds(): Flow<String> = state
    .map { (it as? SessionState.SignedIn)?.selectedAccountId }
    .distinctUntilChanged()
    .filterNotNull()
