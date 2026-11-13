package com.tomasps.virga.feature.r2.metrics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomasps.virga.core.auth.session.SessionRepository
import com.tomasps.virga.core.auth.session.selectedAccountIds
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.ui.components.TimeRange
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.feature.r2.api.R2BucketMetrics
import com.tomasps.virga.feature.r2.api.R2ErrorUiMapper
import com.tomasps.virga.feature.r2.api.R2MetricsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** Metrics tab state. [metrics] is null while no account is selected. */
data class R2MetricsUiState(
    val range: TimeRange = TimeRange.TWENTY_FOUR_HOURS,
    val metrics: UiState<R2BucketMetrics>? = null,
)

/**
 * Operation counts and stored size for one bucket over the chosen window. The API serves these as
 * adaptive samples, so the screen states that the numbers are estimates.
 */
class R2MetricsViewModel(
    session: SessionRepository,
    private val repository: R2MetricsRepository,
    private val bucketName: String,
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel() {

    private val mutableState = MutableStateFlow(R2MetricsUiState())
    val state: StateFlow<R2MetricsUiState> = mutableState.asStateFlow()

    private var accountId: String? = null
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            session.selectedAccountIds().collectLatest { id ->
                accountId = id
                load()
            }
        }
    }

    fun selectRange(range: TimeRange) {
        mutableState.update { it.copy(range = range) }
        load()
    }

    fun retry() = load()

    private fun load() {
        val id = accountId
        loadJob?.cancel()
        if (id == null) {
            mutableState.update { it.copy(metrics = null) }
            return
        }
        mutableState.update { it.copy(metrics = UiState.Loading) }
        val window = state.value.range
        loadJob = viewModelScope.launch {
            val end = Instant.now(clock)
            val start = end.minus(Duration.ofHours(window.hours.toLong()))
            val outcome = when (val result = repository.bucketMetrics(id, bucketName, start, end)) {
                is ApiResult.Success -> UiState.Content(result.value)
                is ApiResult.Failure -> UiState.Failure(R2ErrorUiMapper.toErrorUi(result.error))
            }
            mutableState.update { current -> if (current.range == window) current.copy(metrics = outcome) else current }
        }
    }
}
