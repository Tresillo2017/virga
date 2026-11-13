package com.tomasps.virga.feature.d1.detail.timetravel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomasps.virga.core.auth.session.SessionRepository
import com.tomasps.virga.core.auth.session.selectedAccountIds
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.ui.state.ErrorUi
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.feature.d1.data.DatabasesRepository
import com.tomasps.virga.feature.d1.data.TimeTravelRepository
import com.tomasps.virga.feature.d1.ui.D1ErrorMapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.OffsetDateTime

/** Where a restore goes: a bookmark, or an ISO 8601 timestamp the API turns into one. */
sealed interface RestoreTarget {
    data class Bookmark(val bookmark: String) : RestoreTarget
    data class Timestamp(val isoTimestamp: String) : RestoreTarget
}

data class TimeTravelUiState(
    val databaseName: String? = null,
    val currentBookmark: UiState<String> = UiState.Loading,
    val lookupInput: String = "",
    val lookupBookmark: String? = null,
    val lookupError: ErrorUi? = null,
    val pendingRestore: RestoreTarget? = null,
    val isRestoring: Boolean = false,
    val restoredMessage: String? = null,
    val undoBookmark: String? = null,
    val actionError: ErrorUi? = null,
)

/**
 * Time Travel: the current bookmark, a bookmark for a past timestamp, and restore. A restore overwrites the database
 * and cancels in-flight queries, so [requestRestore] only stages it; the screen asks for the typed database name.
 */
class TimeTravelViewModel(
    session: SessionRepository,
    private val databases: DatabasesRepository,
    private val timeTravel: TimeTravelRepository,
    private val databaseId: String,
) : ViewModel() {

    private val mutableState = MutableStateFlow(TimeTravelUiState())
    val state: StateFlow<TimeTravelUiState> = mutableState.asStateFlow()

    private var accountId: String? = null

    init {
        viewModelScope.launch {
            session.selectedAccountIds().collectLatest { id ->
                accountId = id
                mutableState.update { TimeTravelUiState() }
                loadHeader(id)
            }
        }
    }

    fun refresh() {
        accountId?.let { id -> viewModelScope.launch { loadHeader(id) } }
    }

    /** A bookmark belongs to the timestamp it was looked up for, so editing the timestamp drops it. */
    fun onLookupChanged(input: String) = mutableState.update {
        it.copy(lookupInput = input, lookupError = null, lookupBookmark = null)
    }

    fun lookUp() {
        val id = accountId ?: return
        val input = mutableState.value.lookupInput.trim()
        if (!isIsoTimestamp(input)) {
            mutableState.update { it.copy(lookupError = invalidTimestamp) }
            return
        }
        viewModelScope.launch {
            when (val result = timeTravel.bookmarkAt(id, databaseId, input)) {
                is ApiResult.Success -> mutableState.update {
                    it.copy(lookupBookmark = result.value.bookmark, lookupError = null)
                }
                is ApiResult.Failure -> mutableState.update {
                    it.copy(lookupBookmark = null, lookupError = D1ErrorMapper.toErrorUi(result.error))
                }
            }
        }
    }

    fun requestRestoreToBookmark(bookmark: String) = mutableState.update { it.copy(pendingRestore = RestoreTarget.Bookmark(bookmark)) }

    fun requestRestoreToTimestamp() {
        val input = mutableState.value.lookupInput.trim()
        if (!isIsoTimestamp(input)) {
            mutableState.update { it.copy(lookupError = invalidTimestamp) }
            return
        }
        mutableState.update { it.copy(pendingRestore = RestoreTarget.Timestamp(input)) }
    }

    fun cancelRestore() = mutableState.update { it.copy(pendingRestore = null) }

    /** Called only after the typed database name matched. */
    fun confirmRestore() {
        val id = accountId ?: return
        val target = mutableState.value.pendingRestore ?: return
        mutableState.update { it.copy(pendingRestore = null, isRestoring = true, restoredMessage = null) }
        viewModelScope.launch {
            val result = when (target) {
                is RestoreTarget.Bookmark -> timeTravel.restoreToBookmark(id, databaseId, target.bookmark)
                is RestoreTarget.Timestamp -> timeTravel.restoreToTimestamp(id, databaseId, target.isoTimestamp)
            }
            when (result) {
                is ApiResult.Success -> {
                    mutableState.update {
                        it.copy(isRestoring = false, restoredMessage = result.value.message, undoBookmark = result.value.previousBookmark)
                    }
                    loadHeader(id)
                }
                is ApiResult.Failure -> mutableState.update {
                    it.copy(isRestoring = false, actionError = D1ErrorMapper.toErrorUi(result.error))
                }
            }
        }
    }

    /** Restores to the bookmark that was current before the last restore. */
    fun requestUndo() {
        mutableState.value.undoBookmark?.let { requestRestoreToBookmark(it) }
    }

    fun dismissActionError() = mutableState.update { it.copy(actionError = null) }

    private suspend fun loadHeader(id: String) {
        mutableState.update { it.copy(currentBookmark = UiState.Loading) }
        val bookmark = when (val result = timeTravel.currentBookmark(id, databaseId)) {
            is ApiResult.Success -> result.value.bookmark?.let { UiState.Content(it) }
                ?: UiState.Failure(D1ErrorMapper.toErrorUi(com.tomasps.virga.core.common.result.ApiError.Decode("no bookmark")))
            is ApiResult.Failure -> UiState.Failure(D1ErrorMapper.toErrorUi(result.error))
        }
        val name = (databases.get(id, databaseId) as? ApiResult.Success)?.value?.name
        mutableState.update { it.copy(currentBookmark = bookmark, databaseName = name) }
    }

    private companion object {
        val invalidTimestamp = ErrorUi(
            title = com.tomasps.virga.core.ui.state.UiText.Resource(com.tomasps.virga.feature.d1.R.string.d1_error_timestamp_title),
            message = com.tomasps.virga.core.ui.state.UiText.Resource(com.tomasps.virga.feature.d1.R.string.d1_error_timestamp_message),
            retryable = false,
        )
    }
}

/** True for an ISO 8601 date-time with an offset, such as 2026-11-10T08:00:00Z. */
internal fun isIsoTimestamp(text: String): Boolean = text.isNotEmpty() && runCatching { OffsetDateTime.parse(text) }.isSuccess
