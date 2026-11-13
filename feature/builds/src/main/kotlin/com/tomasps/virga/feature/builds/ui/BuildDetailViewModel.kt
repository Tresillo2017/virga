package com.tomasps.virga.feature.builds.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomasps.virga.core.common.builds.BuildsGateway
import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.ui.state.ErrorUi
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.core.ui.state.UiText
import com.tomasps.virga.feature.builds.R
import com.tomasps.virga.feature.builds.api.BuildRecord
import com.tomasps.virga.feature.builds.api.BuildsRepository
import com.tomasps.virga.feature.builds.api.LogStream
import com.tomasps.virga.feature.builds.watch.WatchPolicy
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How often an active build and its log are re-read on screen. The watch service has its own cadence. */
internal const val DETAIL_POLL_MILLIS = 3_000L

data class BuildDetailUiState(
    val build: UiState<BuildRecord> = UiState.Loading,
    val isWatched: Boolean = false,
    val isRefreshing: Boolean = false,
    val logLines: List<String> = emptyList(),
    val logError: ErrorUi? = null,
    val logsFinished: Boolean = false,
    val showCancelConfirm: Boolean = false,
    val isCancelling: Boolean = false,
    val message: UiText? = null,
) {
    /** Cancel is offered only while the build can still change state. */
    val canCancel: Boolean
        get() = (build as? UiState.Content)?.data?.isTerminal == false
}

/** One read of the build. A retryable failure keeps the screen polling; a fatal one stops it. */
internal sealed interface BuildFetch {
    data class Read(val record: BuildRecord) : BuildFetch
    data class Retry(val error: ApiError) : BuildFetch
    data class Stop(val error: ApiError) : BuildFetch
}

internal fun classifyBuildFailure(error: ApiError): BuildFetch = when (error) {
    is ApiError.NotFound, is ApiError.Unauthorized, is ApiError.Forbidden -> BuildFetch.Stop(error)
    else -> BuildFetch.Retry(error)
}

/**
 * Wait before the next read after a retryable failure. A rate limit uses the server's Retry-After, bounded. Any other
 * failure doubles the previous wait, starting from [pollMillis] and capped at the watch service's maximum backoff.
 */
internal fun nextRetryDelay(previousMillis: Long, error: ApiError, pollMillis: Long): Long = when (error) {
    is ApiError.RateLimited -> error.retryAfterMillis.coerceIn(pollMillis, WatchPolicy.MAX_RETRY_AFTER_MS)
    else -> (previousMillis * 2).coerceIn(pollMillis, WatchPolicy.MAX_BACKOFF_MS)
}

/**
 * One build: its status and timings, the watch flag, the log that follows an active build, and cancel. Polling
 * continues through transient failures with backoff, stops on a fatal one, and stops once the build has finished
 * after one last log read.
 */
class BuildDetailViewModel(
    private val repository: BuildsRepository,
    private val gateway: BuildsGateway,
    private val accountId: String,
    private val buildUuid: String,
    private val scriptName: String,
    private val pollMillis: Long = DETAIL_POLL_MILLIS,
) : ViewModel() {

    private val mutableState = MutableStateFlow(BuildDetailUiState())
    val state: StateFlow<BuildDetailUiState> = mutableState.asStateFlow()

    private var logStream = LogStream()
    private var pollJob: Job? = null

    init {
        viewModelScope.launch {
            gateway.watchedBuildIds().collect { ids ->
                mutableState.update { it.copy(isWatched = buildUuid in ids) }
            }
        }
        startPolling()
    }

    /** Re-reads the build and its log, and restarts polling if the build is still active. */
    fun refresh() {
        mutableState.update { it.copy(isRefreshing = true) }
        startPolling()
    }

    /** Opens the cancel confirmation only while the build can still be cancelled. A stale notification action is ignored. */
    fun requestCancel() {
        if (!mutableState.value.canCancel) return
        mutableState.update { it.copy(showCancelConfirm = true) }
    }

    fun dismissCancel() = mutableState.update { it.copy(showCancelConfirm = false) }

    fun confirmCancel() {
        if (mutableState.value.isCancelling) return
        mutableState.update { it.copy(showCancelConfirm = false, isCancelling = true) }
        viewModelScope.launch {
            val message = when (repository.cancel(accountId, buildUuid)) {
                is ApiResult.Success -> UiText.Resource(R.string.builds_cancel_requested)
                is ApiResult.Failure -> UiText.Resource(R.string.builds_cancel_failed)
            }
            mutableState.update { it.copy(isCancelling = false, message = message) }
            refresh()
        }
    }

    /** Starts watching this build. Asks the gateway only; the screen has already handled notification permission. */
    fun startWatching() {
        val record = (state.value.build as? UiState.Content)?.data ?: return
        gateway.startWatching(accountId, record.toSummary())
        mutableState.update { it.copy(message = UiText.Resource(R.string.builds_watch_started)) }
    }

    fun stopWatching() {
        gateway.stopWatching(buildUuid)
        mutableState.update { it.copy(message = UiText.Resource(R.string.builds_watch_stopped)) }
    }

    fun messageShown() = mutableState.update { it.copy(message = null) }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            var retryMillis = pollMillis
            while (true) {
                val wait = when (val fetch = fetchBuild()) {
                    is BuildFetch.Read -> {
                        readLog()
                        if (fetch.record.isTerminal) {
                            mutableState.update { it.copy(logsFinished = true) }
                            break
                        }
                        retryMillis = pollMillis
                        pollMillis
                    }
                    is BuildFetch.Retry -> {
                        retryMillis = nextRetryDelay(retryMillis, fetch.error, pollMillis)
                        retryMillis
                    }
                    is BuildFetch.Stop -> break
                }
                delay(wait)
            }
            mutableState.update { it.copy(isRefreshing = false) }
        }
    }

    private suspend fun fetchBuild(): BuildFetch {
        val fetch = when (val result = repository.build(accountId, buildUuid, scriptName)) {
            is ApiResult.Success -> BuildFetch.Read(result.value)
            is ApiResult.Failure -> classifyBuildFailure(result.error)
        }
        when (fetch) {
            is BuildFetch.Read -> mutableState.update { it.copy(build = UiState.Content(fetch.record)) }
            is BuildFetch.Retry -> showBuildFailure(fetch.error, fatal = false)
            is BuildFetch.Stop -> showBuildFailure(fetch.error, fatal = true)
        }
        // A read is the end of a manual refresh. An active build polls forever, so the loop's own end never clears it.
        mutableState.update { it.copy(isRefreshing = false) }
        return fetch
    }

    /** Keeps a shown build on screen and reports the failure as a message; shows the error when there is no build yet. */
    private fun showBuildFailure(error: ApiError, fatal: Boolean) {
        mutableState.update { current ->
            when {
                current.build is UiState.Content && fatal -> current.copy(message = buildsErrorUi(error).title)
                current.build is UiState.Content -> current
                else -> current.copy(build = UiState.Failure(buildsErrorUi(error)))
            }
        }
    }

    private suspend fun readLog() {
        val requestCursor = logStream.cursor
        when (val result = repository.logs(accountId, buildUuid, requestCursor)) {
            is ApiResult.Success -> {
                logStream = logStream.applied(result.value, requestCursor)
                mutableState.update { it.copy(logLines = logStream.lines, logError = null) }
            }
            is ApiResult.Failure -> mutableState.update {
                it.copy(logError = buildsErrorUi(result.error))
            }
        }
    }
}
