package com.tomasps.virga.feature.builds.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.ui.components.LoadMoreState
import com.tomasps.virga.core.ui.components.loadMoreStateOf
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.core.ui.state.UiText
import com.tomasps.virga.feature.builds.R
import com.tomasps.virga.feature.builds.api.BuildRecord
import com.tomasps.virga.feature.builds.api.BuildTarget
import com.tomasps.virga.feature.builds.api.BuildsPage
import com.tomasps.virga.feature.builds.api.BuildsRepository
import com.tomasps.virga.feature.builds.api.TriggerRef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal const val BUILD_PAGE_SIZE = 20
private const val FIRST_PAGE = 1

/** Screen state for a Worker's build history. [builds] is Content once the first page has loaded. */
data class WorkerBuildsUiState(
    val builds: UiState<List<BuildRecord>> = UiState.Loading,
    val isRefreshing: Boolean = false,
    val page: Int = 0,
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    val loadMoreFailed: Boolean = false,
    val showStartSheet: Boolean = false,
    val triggers: List<TriggerRef> = emptyList(),
    val triggersLoaded: Boolean = false,
    val isStarting: Boolean = false,
    val startSheetError: UiText? = null,
    /** A start the user has asked for and not yet confirmed. Nothing is sent until it is confirmed. */
    val pendingStart: BuildTarget? = null,
    val message: UiText? = null,
) {
    val loadMoreState: LoadMoreState
        get() = loadMoreStateOf(hasMore = hasMore, isLoading = isLoadingMore, hasFailed = loadMoreFailed)
}

/**
 * The build target the start sheet asks for. Blank input gives null, so the sheet can ask for a value instead of
 * sending an empty branch or commit.
 */
internal fun buildTargetFor(byCommit: Boolean, value: String): BuildTarget? {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return null
    return if (byCommit) BuildTarget.Commit(trimmed) else BuildTarget.Branch(trimmed)
}

/** A full page means there may be another. Judged from the raw item count, so one malformed item cannot end history. */
internal fun hasMorePages(page: BuildsPage, pageSize: Int): Boolean = page.rawCount >= pageSize

/** Build history of one Worker, paged. The tag is resolved once and reused for every page. */
class WorkerBuildsViewModel(
    private val repository: BuildsRepository,
    private val accountId: String,
    private val scriptName: String,
    private val pageSize: Int = BUILD_PAGE_SIZE,
) : ViewModel() {

    private val mutableState = MutableStateFlow(WorkerBuildsUiState())
    val state: StateFlow<WorkerBuildsUiState> = mutableState.asStateFlow()

    private var workerTag: String? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            mutableState.update { it.copy(isRefreshing = true) }
            when (val result = fetchPage(FIRST_PAGE)) {
                is ApiResult.Success -> mutableState.update { current ->
                    current.copy(
                        builds = UiState.Content(result.value.records.distinctBy { it.buildUuid }),
                        isRefreshing = false,
                        page = FIRST_PAGE,
                        hasMore = hasMorePages(result.value, pageSize),
                        loadMoreFailed = false,
                    )
                }
                is ApiResult.Failure -> mutableState.update { current ->
                    current.copy(
                        builds = UiState.Failure(buildsErrorUi(result.error)),
                        isRefreshing = false,
                    )
                }
            }
        }
    }

    fun loadMore() {
        val current = mutableState.value
        if (!current.hasMore || current.isLoadingMore) return
        viewModelScope.launch {
            mutableState.update { it.copy(isLoadingMore = true, loadMoreFailed = false) }
            val nextPage = current.page + 1
            when (val result = fetchPage(nextPage)) {
                is ApiResult.Success -> mutableState.update { state ->
                    val existing = (state.builds as? UiState.Content)?.data.orEmpty()
                    state.copy(
                        builds = UiState.Content((existing + result.value.records).distinctBy { it.buildUuid }),
                        isLoadingMore = false,
                        page = nextPage,
                        hasMore = hasMorePages(result.value, pageSize),
                    )
                }
                is ApiResult.Failure -> mutableState.update { it.copy(isLoadingMore = false, loadMoreFailed = true) }
            }
        }
    }

    /**
     * Opens the start sheet and loads its triggers. A failed load is shown as its error and is not remembered, so the
     * next open asks again.
     */
    fun openStartSheet() {
        mutableState.update { it.copy(showStartSheet = true, startSheetError = null) }
        if (mutableState.value.triggersLoaded) return
        viewModelScope.launch {
            when (val result = loadTriggers()) {
                is ApiResult.Success -> mutableState.update {
                    it.copy(triggers = result.value, triggersLoaded = true)
                }
                is ApiResult.Failure -> mutableState.update {
                    it.copy(startSheetError = UiText.Resource(R.string.builds_trigger_load_failed))
                }
            }
        }
    }

    fun closeStartSheet() = mutableState.update { it.copy(showStartSheet = false, startSheetError = null) }

    /** Asks the user to confirm a start. The build itself is not sent here. */
    fun requestStart(target: BuildTarget) {
        if (mutableState.value.triggers.isEmpty()) {
            mutableState.update { it.copy(startSheetError = UiText.Resource(R.string.builds_trigger_none)) }
            return
        }
        mutableState.update { it.copy(pendingStart = target, startSheetError = null) }
    }

    fun dismissStart() = mutableState.update { it.copy(pendingStart = null) }

    /** Starts the confirmed build from the Worker's first trigger. */
    fun confirmStart() {
        val target = mutableState.value.pendingStart ?: return
        val trigger = mutableState.value.triggers.firstOrNull()
        if (trigger == null) {
            mutableState.update { it.copy(pendingStart = null, startSheetError = UiText.Resource(R.string.builds_trigger_none)) }
            return
        }
        mutableState.update { it.copy(isStarting = true, pendingStart = null, startSheetError = null) }
        viewModelScope.launch {
            val result = repository.startBuild(accountId, trigger.triggerUuid, target)
            val message = when (result) {
                is ApiResult.Success -> UiText.Resource(R.string.builds_trigger_requested)
                is ApiResult.Failure -> UiText.Resource(R.string.builds_trigger_failed)
            }
            mutableState.update {
                it.copy(isStarting = false, showStartSheet = false, message = message)
            }
            if (result is ApiResult.Success) refresh()
        }
    }

    fun messageShown() = mutableState.update { it.copy(message = null) }

    private suspend fun loadTriggers(): ApiResult<List<TriggerRef>> = when (val tag = resolveTag()) {
        is ApiResult.Failure -> tag
        is ApiResult.Success -> repository.triggers(accountId, tag.value)
    }

    private suspend fun fetchPage(page: Int): ApiResult<BuildsPage> = when (val tag = resolveTag()) {
        is ApiResult.Failure -> tag
        is ApiResult.Success -> repository.buildsPage(accountId, tag.value, scriptName, page, pageSize)
    }

    private suspend fun resolveTag(): ApiResult<String> {
        workerTag?.let { return ApiResult.Success(it) }
        return when (val resolved = repository.tagFor(accountId, scriptName)) {
            is ApiResult.Failure -> resolved
            is ApiResult.Success -> resolved.also { workerTag = it.value }
        }
    }
}
