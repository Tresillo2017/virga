package com.tomasps.virga.feature.account.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomasps.virga.core.auth.session.SessionRepository
import com.tomasps.virga.core.auth.session.SessionState
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.ui.state.ApiErrorUiMapper
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.feature.account.details.AccountDetails
import com.tomasps.virga.feature.account.details.AccountDetailsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** [details] is null when no account is selected, for example in demo mode without accounts or while signed out. */
data class AccountHomeUiState(
    val accountName: String? = null,
    val accountCount: Int = 0,
    val isDemo: Boolean = false,
    val details: UiState<AccountDetails>? = null,
)

/**
 * Header for the Account tab. Reloads account details whenever the selected account changes, so the
 * screen never shows the details of a previous account.
 */
class AccountHomeViewModel(
    session: SessionRepository,
    private val repository: AccountDetailsRepository,
) : ViewModel() {

    private val mutableState = MutableStateFlow(AccountHomeUiState())
    val state: StateFlow<AccountHomeUiState> = mutableState.asStateFlow()

    private var loadedAccountId: String? = null

    init {
        viewModelScope.launch {
            // StateFlow already skips equal values, so each change here is a real change of session. collectLatest
            // cancels the load for the previous account, so a slow request cannot hold up the switch.
            session.state.collectLatest { sessionState -> onSessionChanged(sessionState) }
        }
    }

    fun retry() {
        loadedAccountId?.let { accountId -> viewModelScope.launch { load(accountId) } }
    }

    private suspend fun onSessionChanged(sessionState: SessionState) {
        val header = sessionState.toHeader()
        val accountId = (sessionState as? SessionState.SignedIn)?.selectedAccountId
        val details = if (accountId == null) null else UiState.Loading
        mutableState.update { header.copy(details = details) }
        loadedAccountId = accountId
        if (accountId != null) load(accountId)
    }

    private suspend fun load(accountId: String) {
        mutableState.update { it.copy(details = UiState.Loading) }
        val outcome = when (val result = repository.details(accountId)) {
            is ApiResult.Success -> UiState.Content(result.value)
            is ApiResult.Failure -> UiState.Failure(ApiErrorUiMapper.toErrorUi(result.error))
        }
        mutableState.update { current -> if (loadedAccountId == accountId) current.copy(details = outcome) else current }
    }
}

private fun SessionState.toHeader(): AccountHomeUiState = when (this) {
    is SessionState.SignedIn -> AccountHomeUiState(
        accountName = selectedAccount?.let { it.name.ifBlank { it.id } },
        accountCount = accounts.size,
        isDemo = isDemo,
    )
    SessionState.Restoring, SessionState.SignedOut -> AccountHomeUiState()
}
