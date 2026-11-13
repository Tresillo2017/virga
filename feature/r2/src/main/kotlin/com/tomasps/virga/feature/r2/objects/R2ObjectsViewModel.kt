package com.tomasps.virga.feature.r2.objects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomasps.virga.core.auth.session.SessionRepository
import com.tomasps.virga.core.auth.session.selectedAccountIds
import com.tomasps.virga.core.common.formatBytes
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.ui.components.LoadMoreState
import com.tomasps.virga.core.ui.components.loadMoreStateOf
import com.tomasps.virga.core.ui.state.ErrorUi
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.core.ui.state.UiText
import com.tomasps.virga.feature.r2.R
import com.tomasps.virga.feature.r2.api.R2ErrorUiMapper
import com.tomasps.virga.feature.r2.api.R2ObjectItem
import com.tomasps.virga.feature.r2.api.R2Repository
import com.tomasps.virga.feature.r2.api.R2_SINGLE_PUT_MAX_BYTES
import com.tomasps.virga.feature.r2.transfer.R2DocumentStreams
import com.tomasps.virga.feature.r2.transfer.R2Transfer
import com.tomasps.virga.feature.r2.transfer.R2TransferCoordinator
import com.tomasps.virga.feature.r2.transfer.R2TransferDirection
import com.tomasps.virga.feature.r2.transfer.R2TransferStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One level of the folder view: the folders (common prefixes) and the objects directly under [folderPrefix]. */
data class R2ObjectListing(
    val folders: List<String>,
    val objects: List<R2ObjectItem>,
)

data class R2ObjectsUiState(
    val folderPrefix: String = "",
    val search: String = "",
    val listing: UiState<R2ObjectListing>? = null,
    val nextCursor: String? = null,
    val isLoadingMore: Boolean = false,
    val loadMoreFailed: Boolean = false,
    val isRefreshing: Boolean = false,
    val selectedKeys: Set<String> = emptySet(),
    val details: R2ObjectItem? = null,
    val pendingDelete: List<String>? = null,
    val isDeleting: Boolean = false,
    /** An upload waiting for the user to confirm it replaces an object that already has this key. */
    val pendingOverwrite: R2PendingUpload? = null,
    val actionError: ErrorUi? = null,
    val message: UiText? = null,
) {
    val isSearching: Boolean get() = search.isNotBlank()

    val loadMore: LoadMoreState
        get() = loadMoreStateOf(hasMore = nextCursor != null, isLoading = isLoadingMore, hasFailed = loadMoreFailed)
}

/** An upload the user has picked and that is waiting to be started, or to be confirmed as an overwrite. */
data class R2PendingUpload(
    val key: String,
    val sourceUri: String,
    val contentType: String?,
    val size: Long,
)

/**
 * Object browser for one bucket: folder navigation by prefix and delimiter, prefix search, paging, object details,
 * single and multi delete, and the hand-off of downloads and uploads to the app-scoped [R2TransferCoordinator].
 */
class R2ObjectsViewModel(
    session: SessionRepository,
    private val repository: R2Repository,
    private val coordinator: R2TransferCoordinator,
    private val streams: R2DocumentStreams,
    private val bucketName: String,
) : ViewModel() {

    private val mutableState = MutableStateFlow(R2ObjectsUiState())
    val state: StateFlow<R2ObjectsUiState> = mutableState.asStateFlow()

    private val selectedAccount = MutableStateFlow<String?>(null)

    /** Transfers that belong to this bucket under the selected account, newest last. */
    val transfers: StateFlow<List<R2Transfer>> = combine(coordinator.transfers, selectedAccount) { list, account ->
        list.filter { it.bucketName == bucketName && it.accountId == account }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var accountId: String? = null
    private var pendingDownload: R2ObjectItem? = null
    private var loadJob: Job? = null
    private var loadMoreJob: Job? = null
    private var searchJob: Job? = null

    /** Bumped by every reload. A page requested under an older folder, search or account is dropped on arrival. */
    private var generation = 0

    /** Uploads already seen as completed, so each finished upload reloads the listing once. */
    private var reportedUploads: Set<String> = emptySet()

    init {
        viewModelScope.launch {
            session.selectedAccountIds().collectLatest { id ->
                accountId = id
                selectedAccount.value = id
                reload(refreshing = false)
            }
        }
        viewModelScope.launch {
            transfers.collect { list ->
                val finished = completedUploadIds(list, reportedUploads)
                if (finished.isNotEmpty()) {
                    reportedUploads = reportedUploads + finished
                    refresh()
                }
            }
        }
    }

    fun openFolder(prefix: String) = navigateTo(prefix)

    fun goUp() = navigateTo(parentPrefix(state.value.folderPrefix))

    /** Waits for a pause in typing before searching, so each keystroke does not send a request. */
    fun onSearchChanged(text: String) {
        mutableState.update { it.copy(search = text, selectedKeys = emptySet()) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            reload(refreshing = false)
        }
    }

    fun refresh() = reload(refreshing = true)

    fun retry() = reload(refreshing = false)

    fun loadMore() {
        val id = accountId ?: return
        val current = state.value
        val cursor = current.nextCursor ?: return
        if (current.isLoadingMore) return
        val requestGeneration = generation
        mutableState.update { it.copy(isLoadingMore = true, loadMoreFailed = false) }
        loadMoreJob = viewModelScope.launch {
            val result = repository.listObjects(id, bucketName, prefixFor(current), !current.isSearching, cursor)
            mutableState.update { latest ->
                if (requestGeneration != generation) return@update latest
                when (result) {
                    is ApiResult.Success -> latest.copy(
                        listing = appendPage(latest.listing, result.value.folders, result.value.objects),
                        nextCursor = result.value.nextCursor,
                        isLoadingMore = false,
                    )
                    is ApiResult.Failure -> latest.copy(isLoadingMore = false, loadMoreFailed = true)
                }
            }
        }
    }

    fun toggleSelected(key: String) = mutableState.update { current ->
        val selected = if (key in current.selectedKeys) current.selectedKeys - key else current.selectedKeys + key
        current.copy(selectedKeys = selected)
    }

    fun clearSelection() = mutableState.update { it.copy(selectedKeys = emptySet()) }

    fun showDetails(item: R2ObjectItem) = mutableState.update { it.copy(details = item) }

    fun dismissDetails() = mutableState.update { it.copy(details = null) }

    /** Asks before deleting one or more objects. Nothing is deleted until [confirmDelete]. */
    fun requestDelete(keys: List<String>) {
        if (keys.isEmpty()) return
        mutableState.update { it.copy(pendingDelete = keys, actionError = null) }
    }

    fun dismissDelete() = mutableState.update { it.copy(pendingDelete = null) }

    fun confirmDelete() {
        val id = accountId ?: return
        val keys = state.value.pendingDelete ?: return
        mutableState.update { it.copy(pendingDelete = null, isDeleting = true, actionError = null) }
        viewModelScope.launch {
            val failures = mutableListOf<ErrorUi>()
            var deleted = 0
            keys.forEach { key ->
                when (val result = repository.deleteObject(id, bucketName, key)) {
                    is ApiResult.Success -> {
                        deleted++
                        mutableState.update { it.copy(selectedKeys = it.selectedKeys - key) }
                    }
                    is ApiResult.Failure -> failures += R2ErrorUiMapper.toErrorUi(result.error)
                }
            }
            mutableState.update {
                it.copy(
                    isDeleting = false,
                    details = null,
                    actionError = failures.firstOrNull(),
                    message = deleteMessage(deleted, failures.size),
                )
            }
            reload(refreshing = false)
        }
    }

    /** Remembers the object so the screen can ask the user where to save it. */
    fun prepareDownload(item: R2ObjectItem) {
        pendingDownload = item
    }

    /** Called with the save location the user chose (null when cancelled). Starts the download in the background. */
    fun onDownloadDestination(destinationUri: String?) {
        val item = pendingDownload
        pendingDownload = null
        val id = accountId ?: return
        if (destinationUri == null || item == null) return
        coordinator.startDownload(id, bucketName, item.key, destinationUri, totalBytes = item.size)
        mutableState.update { it.copy(message = UiText.Resource(R.string.r2_download_started, listOf(item.key.substringAfterLast('/')))) }
    }

    /**
     * Called with the file the user picked. Files over the single-PUT limit are refused here with an explanation,
     * before anything is sent. Files whose size cannot be read are refused too, because a PUT needs the length.
     * A key that already holds an object is not overwritten until the user confirms it.
     */
    fun onUploadPicked(sourceUri: String?) {
        val id = accountId ?: return
        if (sourceUri == null) return
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) { streams.describe(sourceUri) }
            val size = info.size
            when {
                size == null -> mutableState.update { it.copy(actionError = uploadUnknownSize()) }
                size > R2_SINGLE_PUT_MAX_BYTES -> mutableState.update { it.copy(actionError = uploadTooLarge(size)) }
                else -> {
                    val name = info.displayName?.takeIf { it.isNotBlank() } ?: DEFAULT_UPLOAD_NAME
                    val upload = R2PendingUpload(state.value.folderPrefix + name, sourceUri, info.mimeType, size)
                    checkThenStart(id, upload)
                }
            }
        }
    }

    /** Asks before replacing an existing object. If the existence check fails, nothing is uploaded. */
    private suspend fun checkThenStart(id: String, upload: R2PendingUpload) {
        when (val exists = repository.objectExists(id, bucketName, upload.key)) {
            is ApiResult.Failure -> mutableState.update { it.copy(actionError = R2ErrorUiMapper.toErrorUi(exists.error)) }
            is ApiResult.Success ->
                if (exists.value) {
                    mutableState.update { it.copy(actionError = null, pendingOverwrite = upload) }
                } else {
                    startUpload(id, upload)
                }
        }
    }

    fun confirmOverwrite() {
        val id = accountId ?: return
        val upload = state.value.pendingOverwrite ?: return
        mutableState.update { it.copy(pendingOverwrite = null) }
        startUpload(id, upload)
    }

    fun dismissOverwrite() = mutableState.update { it.copy(pendingOverwrite = null) }

    private fun startUpload(id: String, upload: R2PendingUpload) {
        coordinator.startUpload(id, bucketName, upload.key, upload.sourceUri, upload.contentType, upload.size)
        val name = upload.key.substringAfterLast('/')
        mutableState.update {
            it.copy(actionError = null, message = UiText.Resource(R.string.r2_upload_started, listOf(name)))
        }
    }

    fun cancelTransfer(transferId: String) = coordinator.cancel(transferId)

    fun dismissTransfer(transferId: String) = coordinator.dismiss(transferId)

    fun messageShown() = mutableState.update { it.copy(message = null) }

    private fun navigateTo(prefix: String) {
        mutableState.update { it.copy(folderPrefix = prefix, search = "", selectedKeys = emptySet()) }
        reload(refreshing = false)
    }

    private fun reload(refreshing: Boolean) {
        val id = accountId
        generation++
        loadJob?.cancel()
        loadMoreJob?.cancel()
        if (id == null) {
            mutableState.update { it.copy(listing = null, nextCursor = null, isLoadingMore = false, isRefreshing = false) }
            return
        }
        mutableState.update {
            it.copy(
                listing = if (refreshing) it.listing else UiState.Loading,
                nextCursor = null,
                isLoadingMore = false,
                loadMoreFailed = false,
                isRefreshing = refreshing,
            )
        }
        loadJob = viewModelScope.launch { loadFirstPage(id) }
    }

    private suspend fun loadFirstPage(id: String) {
        val current = state.value
        val outcome = when (val result = repository.listObjects(id, bucketName, prefixFor(current), !current.isSearching, cursor = null)) {
            is ApiResult.Success -> ListingOutcome(
                UiState.Content(R2ObjectListing(result.value.folders, result.value.objects)),
                result.value.nextCursor,
            )
            is ApiResult.Failure -> ListingOutcome(UiState.Failure(R2ErrorUiMapper.toErrorUi(result.error)), null)
        }
        mutableState.update { it.copy(listing = outcome.state, nextCursor = outcome.nextCursor, isRefreshing = false) }
    }

    /** Search is scoped to the folder on screen: "img/" searched from the root matches keys under "img/" only. */
    private fun prefixFor(current: R2ObjectsUiState): String =
        if (current.isSearching) current.folderPrefix + current.search.trim() else current.folderPrefix

    private fun appendPage(current: UiState<R2ObjectListing>?, folders: List<String>, objects: List<R2ObjectItem>): UiState<R2ObjectListing> {
        val existing = (current as? UiState.Content)?.data
        return UiState.Content(
            R2ObjectListing(
                folders = (existing?.folders.orEmpty() + folders).distinct(),
                objects = existing?.objects.orEmpty() + objects,
            ),
        )
    }

    private fun deleteMessage(deleted: Int, failed: Int): UiText = if (failed == 0) {
        UiText.Resource(R.string.r2_objects_deleted, listOf(deleted))
    } else {
        UiText.Resource(R.string.r2_objects_delete_partial, listOf(deleted, failed))
    }

    private fun uploadTooLarge(size: Long): ErrorUi = ErrorUi(
        title = UiText.Resource(R.string.r2_upload_too_large_title),
        message = UiText.Resource(R.string.r2_upload_too_large_message, listOf(formatBytes(size))),
        retryable = false,
    )

    private fun uploadUnknownSize(): ErrorUi = ErrorUi(
        title = UiText.Resource(R.string.r2_upload_unknown_size_title),
        message = UiText.Resource(R.string.r2_upload_unknown_size_message),
        retryable = false,
    )

    private class ListingOutcome(val state: UiState<R2ObjectListing>, val nextCursor: String?)

    private companion object {
        const val DEFAULT_UPLOAD_NAME = "upload"
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}

/** The folder above [prefix]: "a/b/" gives "a/", "a/" and "" give "". Prefixes always end with a slash or are empty. */
internal fun parentPrefix(prefix: String): String {
    val trimmed = prefix.trimEnd('/')
    if (trimmed.isEmpty()) return ""
    val parent = trimmed.substringBeforeLast('/', missingDelimiterValue = "")
    return if (parent.isEmpty()) "" else "$parent/"
}
