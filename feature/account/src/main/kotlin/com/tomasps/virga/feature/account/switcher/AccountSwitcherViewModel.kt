package com.tomasps.virga.feature.account.switcher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomasps.virga.core.auth.session.SessionRepository
import com.tomasps.virga.core.auth.session.SessionState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AccountRow(val id: String, val name: String, val isSelected: Boolean)

data class AccountSwitcherUiState(
    val rows: List<AccountRow> = emptyList(),
    val isDemo: Boolean = false,
)

class AccountSwitcherViewModel(private val session: SessionRepository) : ViewModel() {

    val state: StateFlow<AccountSwitcherUiState> = session.state
        .map { it.toSwitcherState() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, session.state.value.toSwitcherState())

    /** Persists the choice, then calls [onDone] so the screen can pop back. */
    fun onAccountSelected(accountId: String, onDone: () -> Unit) {
        viewModelScope.launch {
            session.selectAccount(accountId)
            onDone()
        }
    }
}

private fun SessionState.toSwitcherState(): AccountSwitcherUiState = when (this) {
    is SessionState.SignedIn -> AccountSwitcherUiState(
        rows = accounts.map { account ->
            AccountRow(
                id = account.id,
                name = account.name.ifBlank { account.id },
                isSelected = account.id == selectedAccountId,
            )
        },
        isDemo = isDemo,
    )
    SessionState.Restoring, SessionState.SignedOut -> AccountSwitcherUiState()
}
