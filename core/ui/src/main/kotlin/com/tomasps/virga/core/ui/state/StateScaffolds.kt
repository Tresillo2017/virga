package com.tomasps.virga.core.ui.state

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.ui.R

private val STATE_PADDING = 24.dp
private val STATE_GAP = 8.dp

/** Centred title, optional message and optional action, for a list or screen with nothing to show. */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(STATE_PADDING),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (message != null) {
            Spacer(Modifier.height(STATE_GAP))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null) {
            Spacer(Modifier.height(STATE_PADDING))
            action()
        }
    }
}

/** Friendly error text from [error], with a retry button when the error is retryable and [onRetry] is set. */
@Composable
fun ErrorState(
    error: ErrorUi,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(STATE_PADDING),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = error.title.asString(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(STATE_GAP))
        Text(
            text = error.message.asString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (error.retryable && onRetry != null) {
            Spacer(Modifier.height(STATE_PADDING))
            Button(onClick = onRetry) {
                Text(stringResource(R.string.core_ui_retry))
            }
        }
    }
}

/** Centred loading indicator announced as "Loading" by screen readers. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.core_ui_loading)
    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        LoadingIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

/**
 * Renders [UiState]: a loading indicator, the content, or an [ErrorState] with retry when [onRetry] is set.
 *
 * Changing between loading, content and failure fades the outgoing state out and the incoming one in, with the theme's
 * effects spec. A new value of the same kind updates in place, so content keeps its remembered state while it refreshes.
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
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    AnimatedContent(
        targetState = state,
        modifier = modifier,
        transitionSpec = { fadeIn(effects) togetherWith fadeOut(effects) },
        contentKey = { it::class },
        label = "UiStateContent",
    ) { target ->
        when (target) {
            is UiState.Loading -> LoadingState()
            is UiState.Content -> Column(modifier = Modifier.fillMaxWidth()) { content(target.data) }
            is UiState.Failure -> ErrorState(error = target.error, onRetry = onRetry)
        }
    }
}
