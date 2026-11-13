package com.tomasps.virga.feature.kv.metrics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomasps.virga.core.auth.session.SessionRepository
import com.tomasps.virga.core.auth.session.selectedAccountIds
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.ui.state.ApiErrorUiMapper
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.feature.kv.data.KvMetrics
import com.tomasps.virga.feature.kv.data.KvMetricsSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** Days of analytics to show. The GraphQL datasets keep 31 days, so 30 covers the whole retained window. */
const val KV_METRICS_WINDOW_DAYS = 30L

data class KvMetricsUiState(
    val metrics: UiState<KvMetrics>? = null,
    val isRefreshing: Boolean = false,
)

/** Loads the operations and storage analytics for one namespace over the last [KV_METRICS_WINDOW_DAYS] days. */
class KvMetricsViewModel(
    private val namespaceId: String,
    session: SessionRepository,
    private val source: KvMetricsSource,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val today: () -> LocalDate = { LocalDate.now(zone) },
) : ViewModel() {

    private val mutableState = MutableStateFlow(KvMetricsUiState())
    val state: StateFlow<KvMetricsUiState> = mutableState.asStateFlow()

    private var accountId: String? = null

    init {
        viewModelScope.launch {
            session.selectedAccountIds().collectLatest { id ->
                accountId = id
                load(id)
            }
        }
    }

    fun refresh() {
        val id = accountId ?: return
        viewModelScope.launch {
            mutableState.update { it.copy(isRefreshing = true) }
            load(id)
            mutableState.update { it.copy(isRefreshing = false) }
        }
    }

    private suspend fun load(id: String) {
        mutableState.update { it.copy(metrics = UiState.Loading) }
        val end = today()
        val start = end.minusDays(KV_METRICS_WINDOW_DAYS - 1)
        val outcome = when (val result = source.load(id, namespaceId, start, end)) {
            is ApiResult.Success -> UiState.Content(result.value)
            is ApiResult.Failure -> UiState.Failure(ApiErrorUiMapper.toErrorUi(result.error))
        }
        mutableState.update { if (accountId == id) it.copy(metrics = outcome) else it }
    }
}
