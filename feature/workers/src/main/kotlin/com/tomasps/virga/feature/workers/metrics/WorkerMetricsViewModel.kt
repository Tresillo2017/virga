package com.tomasps.virga.feature.workers.metrics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomasps.virga.core.auth.session.SessionRepository
import com.tomasps.virga.core.auth.session.selectedAccountIds
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.ui.components.TimeRange
import com.tomasps.virga.core.ui.state.ApiErrorUiMapper
import com.tomasps.virga.core.ui.state.UiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.time.Instant

private val DEFAULT_RANGE = TimeRange.TWENTY_FOUR_HOURS

/** Metrics tab state. [content] is Loading until the first window arrives. */
data class WorkerMetricsUiState(
    val accountId: String? = null,
    val range: TimeRange = DEFAULT_RANGE,
    val content: UiState<WorkerMetrics> = UiState.Loading,
)

/**
 * Loads one Worker's invocation metrics for the selected account and time range. A range change or a retry cancels
 * the query in flight, so a slow old window never overwrites a newer one.
 */
class WorkerMetricsViewModel(
    session: SessionRepository,
    private val repository: WorkerMetricsRepository,
    private val scriptName: String,
    private val clock: () -> Instant = Instant::now,
) : ViewModel() {

    private val range = MutableStateFlow(DEFAULT_RANGE)
    private val retries = MutableStateFlow(0)

    val state: StateFlow<WorkerMetricsUiState> = combine(
        session.selectedAccountIds(),
        range,
        retries,
    ) { accountId, selected, _ -> accountId to selected }
        .flatMapLatest { (accountId, selected) -> loadFor(accountId, selected) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), WorkerMetricsUiState())

    fun selectRange(selected: TimeRange) {
        range.value = selected
    }

    fun retry() {
        retries.value = retries.value + 1
    }

    private fun loadFor(accountId: String, selected: TimeRange): Flow<WorkerMetricsUiState> = flow {
        emit(WorkerMetricsUiState(accountId = accountId, range = selected, content = UiState.Loading))
        val end = clock()
        val start = end.minusSeconds(selected.seconds)
        val content = when (val result = repository.load(accountId, scriptName, start, end)) {
            is ApiResult.Success -> UiState.Content(result.value)
            is ApiResult.Failure -> UiState.Failure(ApiErrorUiMapper.toErrorUi(result.error))
        }
        emit(WorkerMetricsUiState(accountId = accountId, range = selected, content = content))
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
