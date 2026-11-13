package com.tomasps.virga.feature.builds.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.ui.state.ErrorUi
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.feature.builds.api.BuildConfig
import com.tomasps.virga.feature.builds.api.BuildConfigEdit
import com.tomasps.virga.feature.builds.api.BuildsRepository
import com.tomasps.virga.feature.builds.api.PreviewSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the configuration screen shows once loaded. A failed previews read leaves [previews] empty and sets
 * [previewsError], so the settings still show.
 */
data class BuildConfigContent(
    val config: BuildConfig,
    val previews: List<PreviewSummary>,
    val previewsError: ErrorUi? = null,
)

data class BuildConfigUiState(
    val content: UiState<BuildConfigContent> = UiState.Loading,
    val isSaving: Boolean = false,
    val saveError: ErrorUi? = null,
    val saved: Boolean = false,
)

/**
 * Reads the Worker's repository and build settings and its Previews, and writes changed settings with PATCH.
 * A save sends only the fields the user changed.
 */
class BuildConfigViewModel(
    private val repository: BuildsRepository,
    private val accountId: String,
    private val scriptName: String,
) : ViewModel() {

    private val mutableState = MutableStateFlow(BuildConfigUiState())
    val state: StateFlow<BuildConfigUiState> = mutableState.asStateFlow()

    private var workerTag: String? = null

    init {
        load()
    }

    fun retry() = load()

    /** Clears the one-shot [BuildConfigUiState.saved] flag once the screen has reacted to it. */
    fun savedShown() = mutableState.update { it.copy(saved = false) }

    /** Saves [edit]. An edit with nothing changed skips the call and reports saved. */
    fun save(edit: BuildConfigEdit) {
        if (edit == BuildConfigEdit()) {
            mutableState.update { it.copy(saved = true) }
            return
        }
        viewModelScope.launch {
            mutableState.update { it.copy(isSaving = true, saveError = null, saved = false) }
            when (val tag = resolveTag()) {
                is ApiResult.Failure -> failSave(tag.error)
                is ApiResult.Success -> applySave(tag.value, edit)
            }
        }
    }

    private suspend fun applySave(tag: String, edit: BuildConfigEdit) {
        when (val result = repository.updateConfig(accountId, tag, edit)) {
            is ApiResult.Failure -> failSave(result.error)
            is ApiResult.Success -> mutableState.update { current ->
                val loaded = (current.content as? UiState.Content)?.data
                current.copy(
                    isSaving = false,
                    saved = true,
                    content = UiState.Content(
                        BuildConfigContent(result.value, loaded?.previews.orEmpty(), loaded?.previewsError),
                    ),
                )
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            mutableState.update { it.copy(content = UiState.Loading) }
            val content = when (val tag = resolveTag()) {
                is ApiResult.Failure -> UiState.Failure(buildsErrorUi(tag.error))
                is ApiResult.Success -> loadContent(tag.value)
            }
            mutableState.update { it.copy(content = content) }
        }
    }

    private suspend fun loadContent(tag: String): UiState<BuildConfigContent> = when (val config = repository.config(accountId, tag)) {
        is ApiResult.Failure -> UiState.Failure(buildsErrorUi(config.error))
        is ApiResult.Success -> UiState.Content(
            when (val previews = repository.previews(accountId, tag)) {
                is ApiResult.Success -> BuildConfigContent(config.value, previews.value)
                is ApiResult.Failure -> BuildConfigContent(
                    config = config.value,
                    previews = emptyList(),
                    previewsError = buildsErrorUi(previews.error),
                )
            },
        )
    }

    private suspend fun resolveTag(): ApiResult<String> {
        workerTag?.let { return ApiResult.Success(it) }
        val resolved = repository.tagFor(accountId, scriptName)
        if (resolved is ApiResult.Success) workerTag = resolved.value
        return resolved
    }

    private fun failSave(error: ApiError) {
        mutableState.update { it.copy(isSaving = false, saveError = buildsErrorUi(error)) }
    }
}
