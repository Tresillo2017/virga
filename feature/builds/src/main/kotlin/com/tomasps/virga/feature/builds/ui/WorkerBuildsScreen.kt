package com.tomasps.virga.feature.builds.ui

import android.widget.Toast
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.navigation.LocalNavigator
import com.tomasps.virga.core.navigation.BuildDetail
import com.tomasps.virga.core.navigation.WorkerBuilds
import com.tomasps.virga.core.ui.components.LoadMoreFooter
import com.tomasps.virga.core.ui.components.SectionCard
import com.tomasps.virga.core.ui.dialogs.ConfirmDialog
import com.tomasps.virga.core.ui.components.ListRow
import com.tomasps.virga.core.ui.components.StatusDot
import com.tomasps.virga.core.ui.scaffold.ScreenScaffold
import com.tomasps.virga.core.ui.state.EmptyState
import com.tomasps.virga.core.ui.state.UiStateContent
import com.tomasps.virga.core.common.relativeTime
import com.tomasps.virga.core.common.RelativeUnit
import com.tomasps.virga.core.common.RelativeTime
import com.tomasps.virga.core.ui.state.asString
import com.tomasps.virga.feature.builds.R
import com.tomasps.virga.feature.builds.api.BuildTarget
import com.tomasps.virga.feature.builds.BuildConfigScreen
import com.tomasps.virga.feature.builds.api.BuildRecord
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import java.time.Instant

/** Build history for one Worker: status, branch, commit, author and duration. Pull to refresh; load more at the end. */
@Composable
fun WorkerBuildsRoute(key: WorkerBuilds) {
    val viewModel: WorkerBuildsViewModel = koinViewModel(parameters = { parametersOf(key) })
    val state by viewModel.state.collectAsState()
    val navigator = LocalNavigator.current
    ScreenScaffold(
        title = key.scriptName,
        onBack = { navigator.back() },
        onRefresh = viewModel::refresh,
        isRefreshing = state.isRefreshing,
        actions = {
            TextButton(onClick = { navigator.navigate(BuildConfigScreen(key.accountId, key.scriptName)) }) {
                Text(stringResource(R.string.builds_action_configuration))
            }
        },
    ) { padding ->
        UiStateContent(state = state.builds, modifier = Modifier.fillMaxSize().padding(padding), onRetry = viewModel::refresh) { builds ->
            if (builds.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.builds_history_empty_title),
                    message = stringResource(R.string.builds_history_empty_message),
                )
            } else {
                BuildList(
                    builds = builds,
                    loadMoreState = state.loadMoreState,
                    onLoadMore = viewModel::loadMore,
                    onStartBuild = viewModel::openStartSheet,
                    onOpen = { build ->
                        navigator.navigate(BuildDetail(key.accountId, build.buildUuid, key.scriptName))
                    },
                )
            }
        }
    }
    if (state.showStartSheet) {
        StartBuildSheet(state = state, onStart = viewModel::requestStart, onDismiss = viewModel::closeStartSheet)
    }
    state.pendingStart?.let { target ->
        ConfirmDialog(
            title = stringResource(R.string.builds_start_confirm_title),
            message = stringResource(R.string.builds_start_confirm_message, targetLabel(target)),
            confirmLabel = stringResource(R.string.builds_start_confirm_action),
            onConfirm = viewModel::confirmStart,
            onDismiss = viewModel::dismissStart,
        )
    }
    val message = state.message?.asString()
    val context = LocalContext.current
    LaunchedEffect(message) {
        if (message != null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.messageShown()
        }
    }
}

@Composable
private fun targetLabel(target: BuildTarget): String = when (target) {
    is BuildTarget.Branch -> stringResource(R.string.builds_target_branch, target.branch)
    is BuildTarget.Commit -> stringResource(R.string.builds_target_commit, target.commitHash)
}

@Composable
private fun BuildList(
    builds: List<BuildRecord>,
    loadMoreState: com.tomasps.virga.core.ui.components.LoadMoreState,
    onLoadMore: () -> Unit,
    onStartBuild: () -> Unit,
    onOpen: (BuildRecord) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            OutlinedButton(onClick = onStartBuild, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(stringResource(R.string.builds_trigger_title))
            }
        }
        items(items = builds, key = { it.buildUuid }) { build ->
            SectionCard(
                rows = listOf { BuildRow(build = build, onClick = { onOpen(build) }) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = BUILD_CARD_SPACING),
            )
        }
        item {
            LoadMoreFooter(state = loadMoreState, onLoadMore = onLoadMore)
        }
    }
}

private val BUILD_CARD_SPACING = 4.dp

@Composable
private fun BuildRow(build: BuildRecord, onClick: () -> Unit) {
    val status = stringResource(statusLabelRes(build))
    val headline = build.commitMessage ?: stringResource(R.string.builds_untitled_build, shortBuildId(build.buildUuid))
    val supporting = listOfNotNull(
        build.branch,
        build.commitHash?.take(SHORT_COMMIT_LENGTH),
        build.author,
        buildDurationText(build),
    ).joinToString(" · ").ifEmpty { null }
    ListRow(
        headline = headline,
        supporting = supporting,
        leading = { StatusDot(tone = build.statusTone(), contentDescription = status) },
        trailing = {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                Text(text = status, modifier = Modifier.padding(end = 0.dp))
                Text(text = relativeTimeText(build.createdOn), modifier = Modifier.padding(top = 2.dp))
            }
        },
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun relativeTimeText(instant: Instant): String {
    val now = Instant.now()
    return when (val rel = relativeTime(instant, now)) {
        is RelativeTime.Past -> stringResource(relativeUnitRes(rel.unit), rel.amount)
        is RelativeTime.Future, RelativeTime.JustNow -> stringResource(R.string.builds_time_now)
    }
}

private fun relativeUnitRes(unit: RelativeUnit): Int = when (unit) {
    RelativeUnit.MINUTE -> R.string.builds_time_minutes_ago
    RelativeUnit.HOUR -> R.string.builds_time_hours_ago
    RelativeUnit.DAY -> R.string.builds_time_days_ago
}
