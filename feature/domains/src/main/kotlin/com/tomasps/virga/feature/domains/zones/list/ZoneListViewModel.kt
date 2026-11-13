package com.tomasps.virga.feature.domains.zones.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomasps.virga.core.auth.session.SessionRepository
import com.tomasps.virga.core.auth.session.selectedAccountIds
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.ui.state.ApiErrorUiMapper
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.core.ui.state.UiText
import com.tomasps.virga.feature.domains.R
import com.tomasps.virga.feature.domains.toUiState
import com.tomasps.virga.feature.domains.zones.Zone
import com.tomasps.virga.feature.domains.zones.ZoneRepository
import com.tomasps.virga.feature.domains.zones.filterZones
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** [zones] is null when no account is selected. [createdZoneId] is set once a zone was added, for the screen to open it. */
data class ZoneListUiState(
    val zones: UiState<List<Zone>>? = null,
    val query: String = "",
    val statusFilter: String? = null,
    val refreshing: Boolean = false,
    val addingZone: Boolean = false,
    val creating: Boolean = false,
    /** Why the typed domain name was refused. Shown inside the add form, which stays open. */
    val addError: UiText? = null,
    val message: UiText? = null,
    val createdZoneId: String? = null,
) {
    /** The rows the list shows: the loaded zones after the search and status filter. */
    fun visibleZones(): List<Zone> = (zones as? UiState.Content)?.data
        ?.let { filterZones(it, query, statusFilter) }
        .orEmpty()
}

/**
 * The Domains tab. Lists every zone of the selected account, filters on the device, and adds zones. Reloads when the
 * selected account changes, and a switch cancels a slow load for the previous account.
 */
class ZoneListViewModel(
    session: SessionRepository,
    private val zones: ZoneRepository,
) : ViewModel() {

    private val mutableState = MutableStateFlow(ZoneListUiState())
    val state: StateFlow<ZoneListUiState> = mutableState.asStateFlow()

    private var accountId: String? = null

    init {
        viewModelScope.launch {
            session.selectedAccountIds().collectLatest { id ->
                accountId = id
                mutableState.update { it.copy(zones = UiState.Loading, query = "", statusFilter = null) }
                load(id)
            }
        }
        viewModelScope.launch {
            zones.changed.collect { accountId?.let { id -> load(id) } }
        }
    }

    fun retry() {
        accountId?.let { id -> viewModelScope.launch { load(id) } }
    }

    fun refresh() {
        val id = accountId ?: return
        mutableState.update { it.copy(refreshing = true) }
        viewModelScope.launch { load(id) }
    }

    fun setQuery(query: String) = mutableState.update { it.copy(query = query) }

    fun setStatusFilter(status: String?) = mutableState.update { it.copy(statusFilter = status) }

    fun showAddZone(show: Boolean) = mutableState.update { it.copy(addingZone = show, addError = null) }

    fun messageShown() = mutableState.update { it.copy(message = null) }

    fun createdZoneOpened() = mutableState.update { it.copy(createdZoneId = null) }

    /** Validates the name, then adds the zone. The name must look like a registrable domain. */
    fun addZone(rawName: String) {
        val name = rawName.trim().lowercase()
        val id = accountId
        when {
            id == null -> Unit
            mutableState.value.creating -> Unit
            !DOMAIN_PATTERN.matches(name) -> mutableState.update {
                it.copy(addError = UiText.Resource(R.string.domains_add_invalid_name))
            }
            else -> {
                mutableState.update { it.copy(creating = true, addError = null) }
                viewModelScope.launch { create(id, name) }
            }
        }
    }

    private suspend fun create(accountId: String, name: String) {
        val result = zones.create(accountId, name)
        mutableState.update { current ->
            when (result) {
                is ApiResult.Success -> current.copy(
                    creating = false,
                    addingZone = false,
                    createdZoneId = result.value.id,
                    message = UiText.Resource(R.string.domains_message_zone_added, listOf(name)),
                )
                is ApiResult.Failure -> current.copy(
                    creating = false,
                    addError = ApiErrorUiMapper.toErrorUi(result.error).message,
                )
            }
        }
    }

    private suspend fun load(accountId: String) {
        val result = zones.listAll(accountId)
        mutableState.update { current ->
            if (this.accountId != accountId) {
                current
            } else {
                current.copy(zones = result.toUiState(), refreshing = false)
            }
        }
    }

    private companion object {
        val DOMAIN_PATTERN = Regex("""^([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}$""")
    }
}
