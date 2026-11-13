package com.tomasps.virga.feature.d1.detail.metrics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomasps.virga.core.auth.session.SessionRepository
import com.tomasps.virga.core.auth.session.selectedAccountIds
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.feature.d1.data.DailyPoint
import com.tomasps.virga.feature.d1.data.MetricsRepository
import com.tomasps.virga.feature.d1.ui.D1ErrorMapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneOffset

/** Window of daily analytics shown. The datasets are daily, so shorter windows are not offered. */
const val METRICS_WINDOW_DAYS = 30L

data class MetricsUiState(
    val daily: UiState<List<DailyPoint>> = UiState.Loading,
    val storageBytes: UiState<Long> = UiState.Loading,
)

/** Daily D1 analytics for the last [METRICS_WINDOW_DAYS] days (UTC), plus the largest size seen in that window. */
class MetricsViewModel(
    session: SessionRepository,
    private val repository: MetricsRepository,
    private val databaseId: String,
    private val today: () -> LocalDate = { LocalDate.now(ZoneOffset.UTC) },
) : ViewModel() {

    private val mutableState = MutableStateFlow(MetricsUiState())
    val state: StateFlow<MetricsUiState> = mutableState.asStateFlow()

    private var accountId: String? = null

    init {
        viewModelScope.launch {
            session.selectedAccountIds().collectLatest { id ->
                accountId = id
                load(id)
            }
        }
    }

    fun retry() {
        accountId?.let { id -> viewModelScope.launch { load(id) } }
    }

    private suspend fun load(id: String) {
        val end = today()
        val start = end.minusDays(METRICS_WINDOW_DAYS - 1)
        mutableState.update { MetricsUiState() }
        val daily = when (val result = repository.daily(id, databaseId, start, end)) {
            is ApiResult.Success -> UiState.Content(result.value)
            is ApiResult.Failure -> UiState.Failure(D1ErrorMapper.toErrorUi(result.error))
        }
        mutableState.update { it.copy(daily = daily) }
        val storage = when (val result = repository.storageBytes(id, databaseId, start, end)) {
            is ApiResult.Success -> UiState.Content(result.value)
            is ApiResult.Failure -> UiState.Failure(D1ErrorMapper.toErrorUi(result.error))
        }
        mutableState.update { it.copy(storageBytes = storage) }
    }
}
