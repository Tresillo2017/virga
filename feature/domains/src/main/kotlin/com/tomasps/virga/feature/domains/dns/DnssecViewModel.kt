package com.tomasps.virga.feature.domains.dns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.ui.state.ApiErrorUiMapper
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.core.ui.state.UiText
import com.tomasps.virga.feature.domains.R
import com.tomasps.virga.feature.domains.toUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** DNSSEC state of one zone. [pendingEnable] is set while the owner confirms turning DNSSEC on or off. */
data class DnssecUiState(
    val details: UiState<DnssecDetails> = UiState.Loading,
    val pendingEnable: Boolean? = null,
    val busy: Boolean = false,
    val message: UiText? = null,
)

/** Reads DNSSEC and changes it after confirmation. The DS record is shown after enabling, for the registrar. */
class DnssecViewModel(
    private val zoneId: String,
    private val dnssec: DnssecRepository,
) : ViewModel() {

    private val mutableState = MutableStateFlow(DnssecUiState())
    val state: StateFlow<DnssecUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    fun retry() {
        mutableState.update { it.copy(details = UiState.Loading) }
        viewModelScope.launch { load() }
    }

    fun requestChange(enable: Boolean) = mutableState.update { it.copy(pendingEnable = enable) }

    fun dismissChange() = mutableState.update { it.copy(pendingEnable = null) }

    fun confirmChange() {
        val enable = mutableState.value.pendingEnable ?: return
        mutableState.update { it.copy(pendingEnable = null, busy = true) }
        viewModelScope.launch { apply(enable) }
    }

    fun messageShown() = mutableState.update { it.copy(message = null) }

    private suspend fun load() {
        val result = dnssec.details(zoneId)
        mutableState.update { it.copy(details = result.toUiState()) }
    }

    private suspend fun apply(enable: Boolean) {
        when (val result = dnssec.setEnabled(zoneId, enable)) {
            is ApiResult.Success -> mutableState.update {
                it.copy(
                    busy = false,
                    details = UiState.Content(result.value),
                    message = UiText.Resource(if (enable) R.string.domains_message_dnssec_enabled else R.string.domains_message_dnssec_disabled),
                )
            }
            is ApiResult.Failure -> mutableState.update {
                it.copy(busy = false, message = ApiErrorUiMapper.toErrorUi(result.error).message)
            }
        }
    }
}
