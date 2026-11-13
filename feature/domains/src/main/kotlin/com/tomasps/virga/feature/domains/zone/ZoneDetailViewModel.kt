package com.tomasps.virga.feature.domains.zone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.common.result.map
import com.tomasps.virga.core.ui.components.TimeRange
import com.tomasps.virga.core.ui.state.ApiErrorUiMapper
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.core.ui.state.UiText
import com.tomasps.virga.feature.domains.R
import com.tomasps.virga.feature.domains.analytics.ZoneTrafficRepository
import com.tomasps.virga.feature.domains.toUiState
import com.tomasps.virga.feature.domains.zones.DevelopmentModeSetting
import com.tomasps.virga.feature.domains.zones.PurgeRequest
import com.tomasps.virga.feature.domains.zones.Zone
import com.tomasps.virga.feature.domains.zones.ZoneConnectionsRepository
import com.tomasps.virga.feature.domains.zones.ZoneRepository
import com.tomasps.virga.feature.domains.zones.ZoneSettingsRepository
import com.tomasps.virga.feature.domains.zones.validatePurge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Overview of one zone: loads the zone, traffic, development mode, Worker routes and registrar data, and runs the
 * zone actions after the owner confirms them. Every section loads on its own, so one failing read does not blank the
 * others.
 */
class ZoneDetailViewModel(
    private val zoneId: String,
    private val zones: ZoneRepository,
    private val settings: ZoneSettingsRepository,
    private val connections: ZoneConnectionsRepository,
    private val traffic: ZoneTrafficRepository,
) : ViewModel() {

    private val mutableState = MutableStateFlow(ZoneDetailUiState())
    val state: StateFlow<ZoneDetailUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch { loadAll() }
    }

    fun retry() {
        viewModelScope.launch { loadAll() }
    }

    fun refresh() {
        mutableState.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            loadAll()
            mutableState.update { it.copy(refreshing = false) }
        }
    }

    fun setTimeRange(range: TimeRange) {
        mutableState.update { it.copy(timeRange = range, traffic = UiState.Loading) }
        viewModelScope.launch { loadTraffic(range) }
    }

    fun setDevelopmentMode(on: Boolean) {
        mutableState.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = settings.setDevelopmentMode(zoneId, on)
            mutableState.update { current ->
                when (result) {
                    is ApiResult.Success -> current.copy(busy = false, devMode = UiState.Content(result.value.toStatus()))
                    is ApiResult.Failure -> current.copy(busy = false, message = errorText(result.error))
                }
            }
        }
    }

    fun requestPurge(request: PurgeRequest) {
        val problem = validatePurge(request)
        if (problem != null) {
            mutableState.update { it.copy(message = problem.toText()) }
        } else {
            requestAction(ZoneAction.Purge(request))
        }
    }

    fun requestAction(action: ZoneAction) = mutableState.update { it.copy(pendingAction = action) }

    fun dismissAction() = mutableState.update { it.copy(pendingAction = null) }

    fun confirmAction() {
        val action = mutableState.value.pendingAction ?: return
        mutableState.update { it.copy(pendingAction = null, busy = true) }
        viewModelScope.launch { execute(action) }
    }

    fun messageShown() = mutableState.update { it.copy(message = null) }

    private suspend fun execute(action: ZoneAction) {
        when (action) {
            is ZoneAction.SetPaused -> applyPause(action.paused)
            is ZoneAction.Purge -> applyPurge(action.request)
            ZoneAction.Delete -> applyDelete()
        }
    }

    private suspend fun applyPause(paused: Boolean) {
        val result = zones.setPaused(zoneId, paused)
        mutableState.update { current ->
            when (result) {
                is ApiResult.Success -> current.copy(
                    busy = false,
                    zone = UiState.Content(result.value),
                    message = UiText.Resource(if (paused) R.string.domains_message_paused else R.string.domains_message_resumed),
                )
                is ApiResult.Failure -> current.copy(busy = false, message = errorText(result.error))
            }
        }
    }

    private suspend fun applyPurge(request: PurgeRequest) {
        val result = settings.purge(zoneId, request)
        mutableState.update { current ->
            when (result) {
                is ApiResult.Success -> current.copy(busy = false, message = UiText.Resource(R.string.domains_message_purged))
                is ApiResult.Failure -> current.copy(busy = false, message = errorText(result.error))
            }
        }
    }

    private suspend fun applyDelete() {
        when (val result = zones.delete(zoneId)) {
            is ApiResult.Success -> mutableState.update { it.copy(busy = false, deleted = true) }
            is ApiResult.Failure -> mutableState.update { it.copy(busy = false, message = errorText(result.error)) }
        }
    }

    private suspend fun loadAll() {
        mutableState.update { it.copy(zone = UiState.Loading) }
        val zoneResult = zones.get(zoneId)
        mutableState.update { it.copy(zone = zoneResult.toUiState()) }
        val range = mutableState.value.timeRange
        loadTraffic(range)
        loadDevelopmentMode()
        loadRoutes()
        loadRegistrar(zoneResult.getOrNullAccount())
    }

    private suspend fun loadTraffic(range: TimeRange) {
        val result = traffic.traffic(zoneId, range)
        mutableState.update { current -> if (current.timeRange == range) current.copy(traffic = result.toUiState()) else current }
    }

    private suspend fun loadDevelopmentMode() {
        val result = settings.developmentMode(zoneId).map { it.toStatus() }
        mutableState.update { it.copy(devMode = result.toUiState()) }
    }

    private suspend fun loadRoutes() {
        val result = connections.routes(zoneId)
        mutableState.update { it.copy(routes = result.toUiState()) }
    }

    private suspend fun loadRegistrar(accountId: String?) {
        if (accountId == null) {
            mutableState.update { it.copy(registrar = UiState.Content(null)) }
            return
        }
        val domain = (mutableState.value.zone as? UiState.Content)?.data?.name.orEmpty()
        val registrar = when (val result = connections.registration(accountId, domain)) {
            is ApiResult.Success -> UiState.Content(result.value)
            is ApiResult.Failure ->
                if (result.error.meansNotRegisteredHere()) UiState.Content(null) else UiState.Failure(ApiErrorUiMapper.toErrorUi(result.error))
        }
        mutableState.update { it.copy(registrar = registrar) }
    }

    private fun errorText(error: ApiError): UiText = ApiErrorUiMapper.toErrorUi(error).message
}

private fun DevelopmentModeSetting.toStatus(): DevModeStatus = DevModeStatus(
    on = isOn,
    remainingSeconds = remainingSeconds,
    editable = editable == true,
)

/** The account a zone belongs to, read from the zone result. Null when the zone did not load or has no account. */
private fun ApiResult<Zone>.getOrNullAccount(): String? =
    (this as? ApiResult.Success)?.value?.account?.id?.takeIf { it.isNotEmpty() }
