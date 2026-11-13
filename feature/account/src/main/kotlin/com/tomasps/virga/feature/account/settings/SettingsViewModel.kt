package com.tomasps.virga.feature.account.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomasps.virga.core.auth.session.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.System,
    val isSignOutConfirmVisible: Boolean = false,
)

class SettingsViewModel(
    private val session: SessionRepository,
    private val themes: ThemeRepository,
) : ViewModel() {

    private val isSignOutConfirmVisible = MutableStateFlow(false)

    val state: StateFlow<SettingsUiState> = combine(themes.mode, isSignOutConfirmVisible) { mode, confirm ->
        SettingsUiState(themeMode = mode, isSignOutConfirmVisible = confirm)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    fun onThemeSelected(mode: ThemeMode) {
        viewModelScope.launch { themes.set(mode) }
    }

    fun onSignOutRequested() {
        isSignOutConfirmVisible.update { true }
    }

    fun onSignOutDismissed() {
        isSignOutConfirmVisible.update { false }
    }

    fun onSignOutConfirmed() {
        isSignOutConfirmVisible.update { false }
        viewModelScope.launch { session.signOut() }
    }
}
