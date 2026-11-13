package com.tomasps.virga.feature.kv.namespace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomasps.virga.core.auth.session.SessionRepository
import com.tomasps.virga.core.auth.session.selectedAccountIds
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.ui.components.LoadMoreState
import com.tomasps.virga.core.ui.components.loadMoreStateOf
import com.tomasps.virga.core.ui.state.ApiErrorUiMapper
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.core.ui.state.UiText
import com.tomasps.virga.feature.kv.R
import com.tomasps.virga.feature.kv.changes.KvNamespaceChanges
import com.tomasps.virga.feature.kv.data.KvKeyInfo
import com.tomasps.virga.feature.kv.data.KvKeyPage
import com.tomasps.virga.feature.kv.data.KvRepository
import com.tomasps.virga.feature.kv.kvActionMessage
import com.tomasps.virga.feature.kv.value.KV_MAX_TITLE_CHARS
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything the namespace screen shows: the title, the key list with prefix search and paging, and dialogs. */
data class KvNamespaceUiState(
    val title: String = "",
    val prefix: String = "",
    val keys: UiState<List<KvKeyInfo>>? = null,
    val nextCursor: String? = null,
    val isLoadingMore: Boolean = false,
    val loadMoreFailed: Boolean = false,
    val isRefreshing: Boolean = false,
    val selected: Set<String> = emptySet(),
    val isBusy: Boolean = false,
    val showRename: Boolean = false,
    val showDelete: Boolean = false,
    val showBulkDelete: Boolean = false,
) {
    val loadMore: LoadMoreState
        get() = loadMoreStateOf(hasMore = nextCursor != null, isLoading = isLoadingMore, hasFailed = loadMoreFailed)
}

/** One-shot results for the screen: a snackbar text, or the namespace is gone and the screen should close. */
sealed interface KvNamespaceEvent {
    data class Message(val text: UiText) : KvNamespaceEvent

    data object Closed : KvNamespaceEvent
}

private const val KEY_PAGE_SIZE = 100
private const val MESSAGE_BUFFER = 4
internal const val PREFIX_DEBOUNCE_MILLIS = 300L

/**
 * Key list for one namespace, plus its rename, delete and bulk delete actions. Typing a prefix waits briefly, then
 * cancels the previous listing, so a slow search cannot overwrite a newer one.
 */
class KvNamespaceViewModel(
    private val namespaceId: String,
    session: SessionRepository,
    private val repository: KvRepository,
    private val changes: KvNamespaceChanges,
    private val prefixDebounceMillis: Long = PREFIX_DEBOUNCE_MILLIS,
) : ViewModel() {

    private val mutableState = MutableStateFlow(KvNamespaceUiState())
    val state: StateFlow<KvNamespaceUiState> = mutableState.asStateFlow()

    private val mutableEvents = MutableSharedFlow<KvNamespaceEvent>(extraBufferCapacity = MESSAGE_BUFFER)
    val events: SharedFlow<KvNamespaceEvent> = mutableEvents.asSharedFlow()

    private var accountId: String? = null

    /** The one listing in flight. Any new listing, refresh or prefix change cancels it, so only the latest one lands. */
    private var listJob: Job? = null

    /** The next page in flight. It is cancelled with the listing, so a page for an old prefix can never land. */
    private var loadMoreJob: Job? = null

    init {
        viewModelScope.launch {
            session.selectedAccountIds().collectLatest { id ->
                accountId = id
                startListLoad(id, withTitle = true)
            }
        }
        viewModelScope.launch {
            changes.version.drop(1).collectLatest { accountId?.let { startListLoad(it) } }
        }
    }

    fun refresh() {
        val id = accountId ?: return
        startListLoad(id, withTitle = true, refreshing = true)
    }

    /** Waits briefly after the last keystroke, then lists the new prefix. A page still loading for the old one is dropped. */
    fun setPrefix(prefix: String) {
        mutableState.update { it.copy(prefix = prefix) }
        accountId?.let { startListLoad(it, delayMillis = prefixDebounceMillis) }
    }

    fun loadMore() {
        val current = mutableState.value
        val id = accountId ?: return
        val cursor = current.nextCursor ?: return
        if (current.isLoadingMore) return
        val prefix = current.prefix
        mutableState.update { it.copy(isLoadingMore = true, loadMoreFailed = false) }
        loadMoreJob = viewModelScope.launch {
            when (val result = repository.keyPage(id, namespaceId, prefix, cursor, KEY_PAGE_SIZE)) {
                is ApiResult.Success -> {
                    ensureActive()
                    appendPage(prefix, result.value)
                }
                is ApiResult.Failure -> mutableState.update { it.copy(isLoadingMore = false, loadMoreFailed = true) }
            }
        }
    }

    fun toggleSelected(name: String) {
        mutableState.update { state ->
            val next = if (name in state.selected) state.selected - name else state.selected + name
            state.copy(selected = next)
        }
    }

    fun clearSelection() = mutableState.update { it.copy(selected = emptySet()) }

    fun requestBulkDelete() = mutableState.update { it.copy(showBulkDelete = true) }

    fun dismissDialogs() = mutableState.update { it.copy(showBulkDelete = false, showRename = false, showDelete = false) }

    fun requestRename() = mutableState.update { it.copy(showRename = true) }

    fun requestDelete() = mutableState.update { it.copy(showDelete = true) }

    fun confirmBulkDelete() {
        val id = accountId ?: return
        val names = mutableState.value.selected.toList()
        viewModelScope.launch {
            mutableState.update { it.copy(isBusy = true, showBulkDelete = false) }
            val outcome = deleteKeysInChunks(names) { chunk -> repository.bulkDelete(id, namespaceId, chunk) }
            mutableState.update { it.copy(isBusy = false, selected = emptySet()) }
            mutableEvents.emit(KvNamespaceEvent.Message(bulkDeleteMessage(outcome)))
            startListLoad(id)
        }
    }

    fun rename(newTitle: String) {
        val id = accountId ?: return
        val title = newTitle.trim()
        val problem = titleProblem(title)
        if (problem != null) {
            mutableEvents.tryEmit(KvNamespaceEvent.Message(problem))
            return
        }
        viewModelScope.launch {
            mutableState.update { it.copy(isBusy = true, showRename = false) }
            when (val result = repository.renameNamespace(id, namespaceId, title)) {
                is ApiResult.Success -> {
                    mutableState.update { it.copy(isBusy = false, title = title) }
                    changes.notifyChanged()
                    mutableEvents.emit(KvNamespaceEvent.Message(UiText.Resource(R.string.kv_message_namespace_renamed)))
                }
                is ApiResult.Failure -> {
                    mutableState.update { it.copy(isBusy = false) }
                    mutableEvents.emit(KvNamespaceEvent.Message(kvActionMessage(result.error, isSingleKeyWrite = false)))
                }
            }
        }
    }

    private fun titleProblem(title: String): UiText? = when {
        title.isEmpty() -> UiText.Resource(R.string.kv_error_title_required)
        title.length > KV_MAX_TITLE_CHARS -> UiText.Resource(R.string.kv_error_title_too_long)
        else -> null
    }

    /** Deletes the namespace. The typed-name check happens in the dialog before this is called. */
    fun delete() {
        val id = accountId ?: return
        viewModelScope.launch {
            mutableState.update { it.copy(isBusy = true, showDelete = false) }
            when (val result = repository.deleteNamespace(id, namespaceId)) {
                is ApiResult.Success -> {
                    changes.notifyChanged()
                    mutableEvents.emit(KvNamespaceEvent.Closed)
                }
                is ApiResult.Failure -> {
                    mutableState.update { it.copy(isBusy = false) }
                    mutableEvents.emit(KvNamespaceEvent.Message(kvActionMessage(result.error, isSingleKeyWrite = false)))
                }
            }
        }
    }

    /**
     * Starts the one listing job: it cancels any listing or page still running, then (after [delayMillis]) loads the
     * title if asked and the first page of keys.
     */
    private fun startListLoad(
        id: String,
        delayMillis: Long = 0L,
        withTitle: Boolean = false,
        refreshing: Boolean = false,
    ) {
        listJob?.cancel()
        loadMoreJob?.cancel()
        listJob = viewModelScope.launch {
            if (refreshing) mutableState.update { it.copy(isRefreshing = true) }
            try {
                if (delayMillis > 0) delay(delayMillis)
                if (withTitle) loadTitle(id)
                reload(id)
            } finally {
                if (refreshing) mutableState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    private suspend fun loadTitle(id: String) {
        val title = (repository.namespace(id, namespaceId) as? ApiResult.Success)?.value?.title ?: return
        mutableState.update { if (accountId == id) it.copy(title = title) else it }
    }

    private suspend fun reload(id: String) {
        val prefix = mutableState.value.prefix
        if (mutableState.value.keys !is UiState.Content) {
            mutableState.update { it.copy(keys = UiState.Loading) }
        }
        when (val result = repository.keyPage(id, namespaceId, prefix, cursor = null, limit = KEY_PAGE_SIZE)) {
            is ApiResult.Success -> mutableState.update {
                if (id != accountId) it else it.copy(
                    keys = UiState.Content(result.value.keys),
                    nextCursor = result.value.nextCursor,
                    isLoadingMore = false,
                    loadMoreFailed = false,
                    selected = it.selected.intersect(result.value.keys.map { key -> key.name }.toSet()),
                )
            }
            is ApiResult.Failure -> mutableState.update {
                it.copy(keys = UiState.Failure(ApiErrorUiMapper.toErrorUi(result.error)), isLoadingMore = false)
            }
        }
    }

    /** Adds a page to the list it was requested for. A page for another prefix is dropped. Names are kept unique. */
    private fun appendPage(prefix: String, page: KvKeyPage) = mutableState.update { state ->
        if (state.prefix != prefix) return@update state.copy(isLoadingMore = false)
        val existing = (state.keys as? UiState.Content)?.data.orEmpty()
        state.copy(
            keys = UiState.Content((existing + page.keys).distinctBy { it.name }),
            nextCursor = page.nextCursor,
            isLoadingMore = false,
        )
    }
}
