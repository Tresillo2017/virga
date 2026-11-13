package com.tomasps.virga.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.ui.R

/** What the footer of a paged list shows. [HIDDEN] draws nothing: there is no further page. */
enum class LoadMoreState { HIDDEN, AVAILABLE, LOADING, FAILED }

private val FOOTER_PADDING = 16.dp
private val SPINNER_SIZE = 24.dp

/**
 * Pure choice of footer state. Loading wins over a failure from the previous attempt, a failure wins over
 * "more available", and nothing is shown once the list is complete.
 */
fun loadMoreStateOf(hasMore: Boolean, isLoading: Boolean, hasFailed: Boolean): LoadMoreState = when {
    isLoading -> LoadMoreState.LOADING
    hasFailed -> LoadMoreState.FAILED
    hasMore -> LoadMoreState.AVAILABLE
    else -> LoadMoreState.HIDDEN
}

/** Footer at the end of a paged list: a "Load more" button, a spinner while loading, or a retry after a failure. */
@Composable
fun LoadMoreFooter(
    state: LoadMoreState,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state == LoadMoreState.HIDDEN) return
    val loadingLabel = stringResource(R.string.core_ui_loading_more)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(FOOTER_PADDING),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            LoadMoreState.HIDDEN -> Unit
            LoadMoreState.AVAILABLE -> TextButton(onClick = onLoadMore) {
                Text(stringResource(R.string.core_ui_load_more))
            }
            LoadMoreState.LOADING -> CircularProgressIndicator(
                modifier = Modifier
                    .size(SPINNER_SIZE)
                    .semantics { contentDescription = loadingLabel },
                strokeWidth = 2.dp,
            )
            LoadMoreState.FAILED -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.core_ui_load_more_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onLoadMore) {
                    Text(stringResource(R.string.core_ui_retry))
                }
            }
        }
    }
}
