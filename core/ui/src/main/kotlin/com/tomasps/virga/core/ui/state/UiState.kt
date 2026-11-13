package com.tomasps.virga.core.ui.state

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Screen state for a single load. [Content] holds whatever the screen needs; empty content is the caller's EmptyState. */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>

    data class Content<out T>(val data: T) : UiState<T>

    data class Failure(val error: ErrorUi) : UiState<Nothing>
}

/**
 * Renders [UiState]: a loading indicator, the content, or an [ErrorState] with retry when [onRetry] is set.
 *
 * The content is a vertical stack, so several composables in [content] lay out top to bottom, as their order says.
 * It is a Column, not a Box, because a Box draws its children on top of each other.
 */
@Composable
fun <T> UiStateContent(
    state: UiState<T>,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    content: @Composable (T) -> Unit,
) {
    when (state) {
        is UiState.Loading -> LoadingState(modifier = modifier)
        is UiState.Content -> Column(modifier = modifier) { content(state.data) }
        is UiState.Failure -> ErrorState(error = state.error, modifier = modifier, onRetry = onRetry)
    }
}
