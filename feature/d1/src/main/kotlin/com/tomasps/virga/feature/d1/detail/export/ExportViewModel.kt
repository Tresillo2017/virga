package com.tomasps.virga.feature.d1.detail.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomasps.virga.core.auth.session.SessionRepository
import com.tomasps.virga.core.auth.session.selectedAccountIds
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.ui.state.ErrorUi
import com.tomasps.virga.core.ui.state.UiText
import com.tomasps.virga.feature.d1.R
import com.tomasps.virga.feature.d1.data.ExportFile
import com.tomasps.virga.feature.d1.data.ExportOptions
import com.tomasps.virga.feature.d1.data.ExportPoll
import com.tomasps.virga.feature.d1.data.ExportRepository
import com.tomasps.virga.feature.d1.ui.D1ErrorMapper
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * A finished export. The signed link is a bearer credential for the dump, so it is never printed: [toString] hides it,
 * and the only consumer is the download manager.
 */
class ExportDownload(val filename: String, val signedUrl: String) {
    override fun toString(): String = "ExportDownload(filename=$filename, url=redacted)"
}

sealed interface ExportPhase {
    data object Idle : ExportPhase
    data class Running(val status: String?) : ExportPhase
    data class Ready(val download: ExportDownload) : ExportPhase
    data class Failed(val error: ErrorUi) : ExportPhase
}

data class ExportUiState(
    val options: ExportOptions = ExportOptions(),
    val phase: ExportPhase = ExportPhase.Idle,
)

/**
 * SQL dump through the polling export. The database cannot answer queries while an export runs, and an export that
 * is not polled is cancelled by the server, so [stop] cancels the wait and the export with it.
 */
class ExportViewModel(
    session: SessionRepository,
    private val export: ExportRepository,
    private val databaseId: String,
) : ViewModel() {

    private val mutableState = MutableStateFlow(ExportUiState())
    val state: StateFlow<ExportUiState> = mutableState.asStateFlow()

    private var accountId: String? = null
    private var job: Job? = null

    init {
        viewModelScope.launch {
            session.selectedAccountIds().collectLatest { id ->
                accountId = id
                stop()
                mutableState.update { ExportUiState(options = it.options) }
            }
        }
    }

    fun setStructureOnly(enabled: Boolean) = mutableState.update { it.copy(options = ExportOptions(structureOnly = enabled)) }

    fun setDataOnly(enabled: Boolean) = mutableState.update { it.copy(options = ExportOptions(dataOnly = enabled)) }

    fun start() {
        val id = accountId ?: return
        if (job?.isActive == true) return
        val options = mutableState.value.options
        mutableState.update { it.copy(phase = ExportPhase.Running(status = null)) }
        job = viewModelScope.launch {
            when (val started = export.start(id, databaseId, options)) {
                is ApiResult.Success -> finish(id, started.value)
                is ApiResult.Failure -> fail(D1ErrorMapper.toErrorUi(started.error))
            }
        }
    }

    /** Stops waiting. Because the export is no longer polled, the server cancels it. */
    fun stop() {
        job?.cancel()
        job = null
        if (mutableState.value.phase is ExportPhase.Running) {
            mutableState.update { it.copy(phase = ExportPhase.Idle) }
        }
    }

    fun dismiss() = mutableState.update { it.copy(phase = ExportPhase.Idle) }

    private suspend fun finish(id: String, first: ExportPoll) {
        val result = export.awaitCompletion(id, databaseId, first) { poll ->
            mutableState.update { state ->
                if (state.phase is ExportPhase.Running) state.copy(phase = ExportPhase.Running(poll.status)) else state
            }
        }
        when (result) {
            is ApiResult.Success -> ready(result.value)
            is ApiResult.Failure -> fail(D1ErrorMapper.toErrorUi(result.error))
        }
    }

    private fun ready(file: ExportFile) {
        val url = file.signedUrl.orEmpty()
        if (!url.startsWith(HTTPS_PREFIX)) {
            fail(invalidLink)
            return
        }
        val name = safeDownloadName(file.filename)
        mutableState.update { it.copy(phase = ExportPhase.Ready(ExportDownload(name, url))) }
    }

    private fun fail(error: ErrorUi) = mutableState.update { it.copy(phase = ExportPhase.Failed(error)) }

    private companion object {
        const val HTTPS_PREFIX = "https://"

        val invalidLink = ErrorUi(
            title = UiText.Resource(R.string.d1_error_export_link_title),
            message = UiText.Resource(R.string.d1_error_export_link_message),
            retryable = true,
        )
    }
}
