package com.tomasps.virga.core.ui.state

/** Screen state for a single load. [Content] holds whatever the screen needs; empty content is the caller's EmptyState. */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>

    data class Content<out T>(val data: T) : UiState<T>

    data class Failure(val error: ErrorUi) : UiState<Nothing>
}
