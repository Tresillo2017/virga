package com.tomasps.virga.feature.domains

import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.ui.state.ApiErrorUiMapper
import com.tomasps.virga.core.ui.state.UiState

/** The dashboard page that the "Open in dashboard" rows link to for things the API cannot change. */
internal const val DASHBOARD_URL = "https://dash.cloudflare.com/"

/** Maps a repository result to the screen state the ViewModels expose. Errors go through the shared mapper, never raw text. */
internal fun <T> ApiResult<T>.toUiState(): UiState<T> = when (this) {
    is ApiResult.Success -> UiState.Content(value)
    is ApiResult.Failure -> UiState.Failure(ApiErrorUiMapper.toErrorUi(error))
}
