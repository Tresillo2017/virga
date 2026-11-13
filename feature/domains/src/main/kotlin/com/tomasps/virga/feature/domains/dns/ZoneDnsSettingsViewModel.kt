package com.tomasps.virga.feature.domains.dns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.feature.domains.toUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Read-only zone DNS settings for the DNS settings tab. */
class ZoneDnsSettingsViewModel(
    private val zoneId: String,
    private val repository: ZoneDnsSettingsRepository,
) : ViewModel() {

    private val mutableState = MutableStateFlow<UiState<ZoneDnsSettings>>(UiState.Loading)
    val state: StateFlow<UiState<ZoneDnsSettings>> = mutableState.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    fun retry() {
        mutableState.update { UiState.Loading }
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        mutableState.update { repository.settings(zoneId).toUiState() }
    }
}
