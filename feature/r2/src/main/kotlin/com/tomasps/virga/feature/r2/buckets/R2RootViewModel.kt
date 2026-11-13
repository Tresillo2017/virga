package com.tomasps.virga.feature.r2.buckets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomasps.virga.core.auth.session.SessionRepository
import com.tomasps.virga.core.auth.session.selectedAccountIds
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.ui.components.LoadMoreState
import com.tomasps.virga.core.ui.components.loadMoreStateOf
import com.tomasps.virga.core.ui.state.ErrorUi
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.core.ui.state.UiText
import com.tomasps.virga.feature.r2.R
import com.tomasps.virga.feature.r2.api.R2BucketInfo
import com.tomasps.virga.feature.r2.api.R2ErrorUiMapper
import com.tomasps.virga.feature.r2.api.R2Repository
import com.tomasps.virga.feature.r2.api.R2_BUCKET_NAME_CHARACTERS
import com.tomasps.virga.feature.r2.api.R2_BUCKET_NAME_MAX_LENGTH
import com.tomasps.virga.feature.r2.api.R2_BUCKET_NAME_PATTERN
import com.tomasps.virga.feature.r2.api.R2_LOCATION_HINTS
import com.tomasps.virga.feature.r2.api.R2_STORAGE_CLASSES
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The create-bucket form. [location] and [storageClass] are null when the API default applies. */
data class R2CreateBucketForm(
    val name: String = "",
    val location: String? = null,
    val storageClass: String? = null,
    val nameError: UiText? = null,
    val isSubmitting: Boolean = false,
    val submitError: ErrorUi? = null,
)

/** Everything the bucket list screen shows. [buckets] is null while no account is selected. */
data class R2RootUiState(
    val buckets: UiState<List<R2BucketInfo>>? = null,
    val nextCursor: String? = null,
    val isLoadingMore: Boolean = false,
    val loadMoreFailed: Boolean = false,
    val search: String = "",
    val isRefreshing: Boolean = false,
    val create: R2CreateBucketForm? = null,
    val message: UiText? = null,
) {
    val loadMore: LoadMoreState
        get() = loadMoreStateOf(hasMore = nextCursor != null, isLoading = isLoadingMore, hasFailed = loadMoreFailed)

    val locationOptions: List<String> get() = R2_LOCATION_HINTS
    val storageClassOptions: List<String> get() = R2_STORAGE_CLASSES
}

/** Lists R2 buckets for the selected account, with name search, paging and bucket creation. */
class R2RootViewModel(
    session: SessionRepository,
    private val repository: R2Repository,
) : ViewModel() {

    private val mutableState = MutableStateFlow(R2RootUiState())
    val state: StateFlow<R2RootUiState> = mutableState.asStateFlow()

    private var accountId: String? = null
    private var loadJob: Job? = null
    private var loadMoreJob: Job? = null
    private var searchJob: Job? = null
    private var screenShownBefore = false

    /** Bumped by every reload. A page that was requested under an older generation is dropped when it arrives. */
    private var generation = 0

    init {
        viewModelScope.launch {
            session.selectedAccountIds().collectLatest { id ->
                accountId = id
                reload(refreshing = false)
            }
        }
    }

    fun refresh() = reload(refreshing = true)

    /**
     * Called each time the screen is composed. The bucket screen sits on top of this one, so the list can be stale when
     * the user comes back (a bucket may have been deleted there). The first showing already has the load from [init].
     */
    fun onScreenShown() {
        if (screenShownBefore) refresh() else screenShownBefore = true
    }

    fun retry() = reload(refreshing = false)

    /** Waits for a pause in typing before searching, so each keystroke does not send a request. */
    fun onSearchChanged(text: String) {
        mutableState.update { it.copy(search = text) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            reload(refreshing = false)
        }
    }

    fun loadMore() {
        val id = accountId ?: return
        val cursor = state.value.nextCursor ?: return
        if (state.value.isLoadingMore) return
        val search = state.value.search
        val requestGeneration = generation
        mutableState.update { it.copy(isLoadingMore = true, loadMoreFailed = false) }
        loadMoreJob = viewModelScope.launch {
            val result = repository.listBuckets(id, cursor, search)
            mutableState.update { current ->
                if (requestGeneration != generation) return@update current
                when (result) {
                    is ApiResult.Success -> current.copy(
                        buckets = appendBuckets(current.buckets, result.value.buckets),
                        nextCursor = result.value.nextCursor,
                        isLoadingMore = false,
                    )
                    is ApiResult.Failure -> current.copy(isLoadingMore = false, loadMoreFailed = true)
                }
            }
        }
    }

    fun openCreate() = mutableState.update { it.copy(create = R2CreateBucketForm()) }

    fun dismissCreate() = mutableState.update { it.copy(create = null) }

    fun onCreateNameChanged(name: String) = updateForm {
        val normalized = name.trim().lowercase()
        it.copy(name = normalized, nameError = nameErrorWhileTyping(normalized), submitError = null)
    }

    fun onCreateLocationChanged(location: String?) = updateForm { it.copy(location = location) }

    fun onCreateStorageClassChanged(storageClass: String?) = updateForm { it.copy(storageClass = storageClass) }

    fun submitCreate() {
        val form = state.value.create ?: return
        val id = accountId ?: return
        if (form.isSubmitting) return
        if (!R2_BUCKET_NAME_PATTERN.matches(form.name)) {
            updateForm { it.copy(nameError = UiText.Resource(R.string.r2_bucket_name_invalid)) }
            return
        }
        updateForm { it.copy(isSubmitting = true, submitError = null) }
        viewModelScope.launch {
            when (val result = repository.createBucket(id, form.name, form.location, form.storageClass)) {
                is ApiResult.Success -> {
                    mutableState.update {
                        it.copy(create = null, message = UiText.Resource(R.string.r2_bucket_created, listOf(form.name)))
                    }
                    reload(refreshing = false)
                }
                is ApiResult.Failure -> updateForm {
                    it.copy(isSubmitting = false, submitError = R2ErrorUiMapper.toErrorUi(result.error))
                }
            }
        }
    }

    fun messageShown() = mutableState.update { it.copy(message = null) }

    /**
     * Flags a character or length problem as the user types, so the error is visible before Create is pressed. A name
     * that is only too short is not flagged until Create, because a name is typed one character at a time.
     */
    private fun nameErrorWhileTyping(name: String): UiText? {
        val hasBadCharacter = name.isNotEmpty() && !R2_BUCKET_NAME_CHARACTERS.matches(name)
        val tooLong = name.length > R2_BUCKET_NAME_MAX_LENGTH
        return if (hasBadCharacter || tooLong) UiText.Resource(R.string.r2_bucket_name_invalid) else null
    }

    private fun updateForm(transform: (R2CreateBucketForm) -> R2CreateBucketForm) {
        mutableState.update { current -> current.create?.let { current.copy(create = transform(it)) } ?: current }
    }

    private fun reload(refreshing: Boolean) {
        val id = accountId
        generation++
        loadJob?.cancel()
        loadMoreJob?.cancel()
        if (id == null) {
            mutableState.update { it.copy(buckets = null, nextCursor = null, isLoadingMore = false, isRefreshing = false) }
            return
        }
        mutableState.update {
            it.copy(
                buckets = if (refreshing) it.buckets else UiState.Loading,
                nextCursor = null,
                isLoadingMore = false,
                loadMoreFailed = false,
                isRefreshing = refreshing,
            )
        }
        loadJob = viewModelScope.launch { loadFirstPage(id) }
    }

    private suspend fun loadFirstPage(id: String) {
        val search = state.value.search
        val outcome = when (val result = repository.listBuckets(id, cursor = null, nameContains = search)) {
            is ApiResult.Success -> Outcome(UiState.Content(result.value.buckets), result.value.nextCursor)
            is ApiResult.Failure -> Outcome(UiState.Failure(R2ErrorUiMapper.toErrorUi(result.error)), null)
        }
        mutableState.update {
            it.copy(buckets = outcome.state, nextCursor = outcome.nextCursor, isRefreshing = false)
        }
    }

    private class Outcome(val state: UiState<List<R2BucketInfo>>, val nextCursor: String?)

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }

    private fun appendBuckets(current: UiState<List<R2BucketInfo>>?, more: List<R2BucketInfo>): UiState<List<R2BucketInfo>> {
        val existing = (current as? UiState.Content)?.data.orEmpty()
        return UiState.Content(existing + more)
    }
}
