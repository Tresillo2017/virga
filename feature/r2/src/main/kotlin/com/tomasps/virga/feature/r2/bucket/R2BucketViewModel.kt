package com.tomasps.virga.feature.r2.bucket

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomasps.virga.core.auth.session.SessionRepository
import com.tomasps.virga.core.auth.session.selectedAccountIds
import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.ui.state.ErrorUi
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.core.ui.state.UiText
import com.tomasps.virga.feature.r2.R
import com.tomasps.virga.feature.r2.api.R2BucketInfo
import com.tomasps.virga.feature.r2.api.R2ErrorUiMapper
import com.tomasps.virga.feature.r2.api.R2Repository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Header and delete state for one bucket. [deleted] tells the screen to leave. [bucket] is null with no account. */
data class R2BucketUiState(
    val bucket: UiState<R2BucketInfo>? = null,
    val selectedTab: Int = TAB_OBJECTS,
    val confirmDelete: Boolean = false,
    val isDeleting: Boolean = false,
    val deleted: Boolean = false,
    val deleteError: ErrorUi? = null,
    val message: UiText? = null,
) {
    companion object {
        const val TAB_OBJECTS = 0
        const val TAB_SETTINGS = 1
        const val TAB_METRICS = 2
    }
}

/** Loads one bucket's details and deletes it. The tabs own their own state. Deleting needs an empty bucket. */
class R2BucketViewModel(
    session: SessionRepository,
    private val repository: R2Repository,
    private val bucketName: String,
) : ViewModel() {

    private val mutableState = MutableStateFlow(R2BucketUiState())
    val state: StateFlow<R2BucketUiState> = mutableState.asStateFlow()

    private var accountId: String? = null

    /**
     * The account this screen was opened under. The route key does not name an account, so if the user switches
     * accounts while this screen is open, the bucket is not shown and cannot be deleted from here.
     */
    private var pinnedAccountId: String? = null
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            session.selectedAccountIds().collectLatest { id ->
                accountId = id
                if (pinnedAccountId == null) pinnedAccountId = id
                load(id)
            }
        }
    }

    fun retry() {
        accountId?.let { id -> viewModelScope.launch { load(id) } }
    }

    fun selectTab(index: Int) = mutableState.update { it.copy(selectedTab = index) }

    fun requestDelete() = mutableState.update { it.copy(confirmDelete = true, deleteError = null) }

    fun dismissDelete() = mutableState.update { it.copy(confirmDelete = false) }

    fun confirmDelete() {
        val id = accountId ?: return
        if (state.value.isDeleting) return
        if (id != pinnedAccountId) {
            mutableState.update { it.copy(confirmDelete = false, deleteError = accountChangedUi()) }
            return
        }
        mutableState.update { it.copy(confirmDelete = false, isDeleting = true, deleteError = null) }
        viewModelScope.launch {
            when (val result = repository.deleteBucket(id, bucketName)) {
                is ApiResult.Success -> mutableState.update { it.copy(isDeleting = false, deleted = true) }
                is ApiResult.Failure -> mutableState.update {
                    it.copy(isDeleting = false, deleteError = deleteFailure(result.error))
                }
            }
        }
    }

    fun messageShown() = mutableState.update { it.copy(message = null) }

    private fun load(id: String?) {
        loadJob?.cancel()
        if (id == null) {
            mutableState.update { it.copy(bucket = null) }
            return
        }
        if (pinnedAccountId != null && id != pinnedAccountId) {
            mutableState.update { it.copy(bucket = UiState.Failure(accountChangedUi())) }
            return
        }
        mutableState.update { it.copy(bucket = UiState.Loading) }
        loadJob = viewModelScope.launch {
            val outcome = when (val result = repository.bucket(id, bucketName)) {
                is ApiResult.Success -> UiState.Content(result.value)
                is ApiResult.Failure -> UiState.Failure(R2ErrorUiMapper.toErrorUi(result.error))
            }
            mutableState.update { it.copy(bucket = outcome) }
        }
    }

    private fun accountChangedUi(): ErrorUi = ErrorUi(
        title = UiText.Resource(R.string.r2_bucket_account_changed_title),
        message = UiText.Resource(R.string.r2_bucket_account_changed_message),
        retryable = false,
    )

    /** A refused delete almost always means the bucket still holds something, so say what must be cleared. */
    private fun deleteFailure(error: ApiError): ErrorUi = when {
        error is ApiError.Rejected && error.status == HTTP_CONFLICT -> ErrorUi(
            title = UiText.Resource(R.string.r2_delete_bucket_refused_title),
            message = UiText.Resource(R.string.r2_delete_bucket_refused_message),
            retryable = false,
        )
        else -> R2ErrorUiMapper.toErrorUi(error)
    }

    private companion object {
        const val HTTP_CONFLICT = 409
    }
}
