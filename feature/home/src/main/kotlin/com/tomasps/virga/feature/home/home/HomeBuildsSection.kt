package com.tomasps.virga.feature.home.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
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
import com.tomasps.virga.core.common.builds.BuildStatus
import com.tomasps.virga.core.common.builds.BuildSummary
import com.tomasps.virga.core.ui.components.ListRow
import com.tomasps.virga.core.ui.components.SectionCard
import com.tomasps.virga.core.ui.components.StatusDot
import com.tomasps.virga.core.ui.components.StatusTone
import com.tomasps.virga.core.ui.state.ErrorState
import com.tomasps.virga.core.ui.state.LoadingState
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.feature.home.R

private val NOTE_PADDING_HORIZONTAL = 16.dp

/** The builds running across the account. A null [building] means no account is selected, so nothing is drawn. */
@Composable
internal fun HomeBuildsSection(
    building: UiState<List<BuildSummary>>?,
    watchedBuildIds: Set<String>,
    onWatchRequested: (BuildSummary) -> Unit,
    onStopWatching: (BuildSummary) -> Unit,
    onOpenBuild: (BuildSummary) -> Unit,
    onRetry: () -> Unit,
) {
    when (building) {
        null -> Unit
        UiState.Loading -> LoadingState()
        is UiState.Failure -> ErrorState(error = building.error, onRetry = onRetry)
        is UiState.Content -> BuildList(
            builds = building.data,
            watchedBuildIds = watchedBuildIds,
            onWatchRequested = onWatchRequested,
            onStopWatching = onStopWatching,
            onOpenBuild = onOpenBuild,
        )
    }
}

@Composable
private fun BuildList(
    builds: List<BuildSummary>,
    watchedBuildIds: Set<String>,
    onWatchRequested: (BuildSummary) -> Unit,
    onStopWatching: (BuildSummary) -> Unit,
    onOpenBuild: (BuildSummary) -> Unit,
) {
    if (builds.isEmpty()) {
        Text(
            text = stringResource(R.string.home_build_none),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = NOTE_PADDING_HORIZONTAL, vertical = 8.dp),
        )
        return
    }
    SectionCard(
        rows = builds.map { build ->
            {
                BuildRow(
                    build = build,
                    isWatched = build.buildUuid in watchedBuildIds,
                    onWatchRequested = { onWatchRequested(build) },
                    onStopWatching = { onStopWatching(build) },
                    onOpen = { onOpenBuild(build) },
                )
            }
        },
    )
}

@Composable
private fun BuildRow(
    build: BuildSummary,
    isWatched: Boolean,
    onWatchRequested: () -> Unit,
    onStopWatching: () -> Unit,
    onOpen: () -> Unit,
) {
    val statusLabel = stringResource(buildStatusLabelRes(build.status))
    val detail = buildDetailText(build)
    val supporting = detail?.let { stringResource(R.string.home_build_subtitle, statusLabel, it) } ?: statusLabel
    ListRow(
        headline = build.scriptName,
        supporting = supporting,
        leading = { StatusDot(tone = build.status.tone(), contentDescription = statusLabel) },
        trailing = {
            if (isWatched) {
                WatchingActions(scriptName = build.scriptName, onStop = onStopWatching)
            } else {
                val watchLabel = stringResource(R.string.home_build_watch_description, build.scriptName)
                TextButton(
                    onClick = onWatchRequested,
                    modifier = Modifier.semantics { contentDescription = watchLabel },
                ) {
                    Text(stringResource(R.string.home_build_watch))
                }
            }
        },
        onClick = onOpen,
    )
}

@Composable
private fun WatchingActions(scriptName: String, onStop: () -> Unit) {
    val stopLabel = stringResource(R.string.home_build_stop_watching_description, scriptName)
    Column(horizontalAlignment = Alignment.End) {
        Text(stringResource(R.string.home_build_watching), style = MaterialTheme.typography.labelLarge)
        TextButton(
            onClick = onStop,
            modifier = Modifier.semantics { contentDescription = stopLabel },
        ) {
            Text(stringResource(R.string.home_build_stop_watching))
        }
    }
}

/** The friendly label for a build status. Every status has its own word, so "Stopped" is never shown as "Stopping". */
internal fun buildStatusLabelRes(status: BuildStatus): Int = when (status) {
    BuildStatus.RUNNING -> R.string.home_build_status_running
    BuildStatus.QUEUED -> R.string.home_build_status_queued
    BuildStatus.INITIALIZING -> R.string.home_build_status_starting
    BuildStatus.STOPPED -> R.string.home_build_status_stopped
    BuildStatus.UNKNOWN -> R.string.home_build_status_unknown
}

private fun BuildStatus.tone(): StatusTone = when (this) {
    BuildStatus.RUNNING, BuildStatus.QUEUED, BuildStatus.INITIALIZING -> StatusTone.Pending
    BuildStatus.STOPPED, BuildStatus.UNKNOWN -> StatusTone.Neutral
}
