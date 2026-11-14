package com.tomasps.virga.feature.builds.ui

import android.widget.Toast

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.ui.draw.clip
import com.tomasps.virga.core.ui.theme.segmentedShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.tomasps.virga.core.common.formatDuration
import com.tomasps.virga.core.navigation.BuildDetail
import com.tomasps.virga.core.navigation.LocalNavigator
import com.tomasps.virga.core.ui.components.CodeBlock
import com.tomasps.virga.core.ui.components.SectionCard
import com.tomasps.virga.core.ui.components.KeyValueRow
import com.tomasps.virga.core.ui.components.OpenInDashboardRow
import com.tomasps.virga.core.ui.components.SectionHeader
import com.tomasps.virga.core.ui.components.StatusDot
import com.tomasps.virga.core.ui.dialogs.ConfirmDialog
import com.tomasps.virga.core.ui.scaffold.ScreenScaffold
import com.tomasps.virga.core.ui.state.ErrorState
import com.tomasps.virga.core.ui.state.LoadingState
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.core.ui.state.asString
import com.tomasps.virga.feature.builds.R
import com.tomasps.virga.feature.builds.api.BuildRecord
import com.tomasps.virga.feature.builds.api.BuildStage
import com.tomasps.virga.feature.builds.api.StageTiming
import com.tomasps.virga.feature.builds.api.stageTimings
import com.tomasps.virga.feature.builds.watch.WatchNotifier
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import java.time.Duration
import java.time.Instant

/** Status, stages with elapsed time, commit, watch and cancel, and the live log of one build. */
@Composable
fun BuildDetailRoute(key: BuildDetail) {
    val viewModel: BuildDetailViewModel = koinViewModel(
        parameters = { parametersOf(key) },
    )
    val state by viewModel.state.collectAsState()
    val navigator = LocalNavigator.current
    // A notification's Cancel action opens this screen with a flag. Ask to confirm once, then clear the flag.
    val launchIntent = LocalContext.current.findActivity()?.intent
    // Waits until the build is known to be cancellable, so a finished build clears nothing and opens nothing.
    LaunchedEffect(launchIntent, key.buildUuid, state.canCancel) {
        val extra = launchIntent?.getStringExtra(WatchNotifier.EXTRA_CONFIRM_CANCEL)
        if (state.canCancel && isCancelRequestFor(extra, key.buildUuid)) {
            launchIntent?.removeExtra(WatchNotifier.EXTRA_CONFIRM_CANCEL)
            viewModel.requestCancel()
        }
    }
    val snackbar = remember { SnackbarHostState() }
    val messageText = state.message?.asString()
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            viewModel.messageShown()
        }
    }
    val watchGate = rememberWatchGate(onGranted = viewModel::startWatching)
    val notifier: WatchNotifier = koinInject()
    val liveUpdatesOff = !notifier.liveUpdatesAllowed()
    val openLiveUpdates = rememberLiveUpdateSettingsOpener()
    val title = (state.build as? UiState.Content)?.data?.scriptName ?: key.workerName
    val subtitle = (state.build as? UiState.Content)?.data?.let { build ->
        listOfNotNull(
            shortBuildIdOrNull(build.buildUuid)?.let { stringResource(R.string.builds_build_id, it) },
            build.branch,
        ).joinToString(separator = " · ")
    }
    ScreenScaffold(
        title = title,
        subtitle = subtitle,
        onBack = { navigator.back() },
        onRefresh = viewModel::refresh,
        isRefreshing = state.isRefreshing,
        snackbarHostState = snackbar,
    ) { padding ->
        when (val build = state.build) {
            is UiState.Loading -> LoadingState(modifier = Modifier.fillMaxSize().padding(padding))
            is UiState.Failure -> ErrorState(error = build.error, onRetry = viewModel::refresh, modifier = Modifier.fillMaxSize().padding(padding))
            is UiState.Content -> BuildDetailBody(
                state = state,
                build = build.data,
                padding = padding,
                onWatch = { watchGate() },
                onStopWatching = viewModel::stopWatching,
                onCancel = viewModel::requestCancel,
                liveUpdatesOff = liveUpdatesOff,
                onOpenLiveUpdates = openLiveUpdates,
            )
        }
    }
    if (state.showCancelConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.builds_cancel_title),
            message = stringResource(R.string.builds_cancel_message),
            confirmLabel = stringResource(R.string.builds_cancel_confirm),
            dismissLabel = stringResource(R.string.builds_cancel_keep),
            destructive = true,
            onConfirm = viewModel::confirmCancel,
            onDismiss = viewModel::dismissCancel,
        )
    }
}

@Composable
private fun BuildDetailBody(
    state: BuildDetailUiState,
    build: BuildRecord,
    padding: PaddingValues,
    onWatch: () -> Unit,
    onStopWatching: () -> Unit,
    onCancel: () -> Unit,
    liveUpdatesOff: Boolean,
    onOpenLiveUpdates: () -> Unit,
) {
    val now = remember(build) { Instant.now() }
    val listState = rememberLazyListState()
    var following by remember { mutableStateOf(true) }
    // Keyed on the measured item count: new lines are in the list only after the frame that lays them out, so the
    // scroll must wait for that count rather than for the line count in composition.
    val measuredItems by remember { derivedStateOf { listState.layoutInfo.totalItemsCount } }
    LaunchedEffect(measuredItems, following) {
        val shouldFollow = following && !build.isTerminal && state.logLines.isNotEmpty()
        // A drag in progress keeps the user's position: the follow choice is made when it ends.
        if (shouldFollow && !listState.isScrollInProgress) listState.scrollBy(SCROLL_TO_LOG_END_PX)
    }
    LaunchedEffect(listState) {
        // A scroll that has just ended decides the follow state. A drag that starts at the bottom and ends away from it
        // pauses following. The first value is skipped: a list that starts taller than the screen is not a scroll.
        snapshotFlow { listState.isScrollInProgress }
            .drop(1)
            .filter { scrolling -> !scrolling }
            .collect { following = nextFollowState(following, userScrolling = true, atBottom = !listState.canScrollForward) }
    }
    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        DetailList(
            listState = listState,
            state = state,
            build = build,
            now = now,
            onWatch = onWatch,
            onStopWatching = onStopWatching,
            onCancel = onCancel,
            liveUpdatesOff = liveUpdatesOff,
            onOpenLiveUpdates = onOpenLiveUpdates,
        )
        // Pinned to the screen, not the list, so it stays reachable after the user has scrolled up into the log.
        if (!following && state.logLines.isNotEmpty()) {
            FilledTonalButton(
                onClick = { following = true },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = JUMP_BOTTOM_PADDING),
            ) {
                Text(stringResource(R.string.builds_log_jump_latest))
            }
        }
    }
}

@Composable
private fun DetailList(
    listState: LazyListState,
    state: BuildDetailUiState,
    build: BuildRecord,
    now: Instant,
    onWatch: () -> Unit,
    onStopWatching: () -> Unit,
    onCancel: () -> Unit,
    liveUpdatesOff: Boolean,
    onOpenLiveUpdates: () -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().testTag(DETAIL_LIST_TAG),
        contentPadding = PaddingValues(start = PAGE_GUTTER, end = PAGE_GUTTER, bottom = 24.dp),
    ) {
        gappedItem { BuildStatusCard(build = build, timings = stageTimings(build, now), now = now) }
        gappedItem { ActionRow(state, build, onWatch, onStopWatching, onCancel) }
        if (state.isWatched && liveUpdatesOff) {
            gappedItem {
                SectionCard(
                    rows = listOf {
                        OpenInDashboardRow(
                            label = stringResource(R.string.builds_live_updates_off),
                            description = stringResource(R.string.builds_live_updates_off_description),
                            onOpen = onOpenLiveUpdates,
                        )
                    },
                )
            }
        }
        gappedItem { SectionCard(rows = detailRows(build)) }
        build.commitMessage?.let { message -> gappedItem { CodeBlock(code = message) } }
        item { SectionHeader(title = stringResource(R.string.builds_log_title)) }
        state.logError?.let { error ->
            gappedItem { Text(text = error.message.asString(), style = MaterialTheme.typography.bodySmall) }
        }
        items(state.logLines.size) { index ->
            LogLine(
                text = state.logLines[index],
                index = index,
                isFirst = index == 0,
                isLast = index == state.logLines.lastIndex,
            )
        }
        gappedItem { LogFooter(state) }
    }
}

/** A list item followed by [SECTION_GAP] of space. Log lines skip this so they form one continuous panel. */
private fun LazyListScope.gappedItem(content: @Composable () -> Unit) {
    item {
        Column(modifier = Modifier.padding(bottom = SECTION_GAP)) { content() }
    }
}

/** Status, the four-stage bar and the elapsed time of the build. */
@Composable
private fun BuildStatusCard(build: BuildRecord, timings: List<StageTiming>, now: Instant) {
    val label = stringResource(statusLabelRes(build))
    val elapsed = elapsedText(build, now)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(CARD_PADDING),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(tone = build.statusTone(), contentDescription = label)
            Spacer(Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = elapsed.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        StageBar(segments = stageSegments(timings, build.isTerminal))
        Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            timings.forEachIndexed { index, timing ->
                val current = stageSegments(timings, build.isTerminal)[index] == StageSegment.CURRENT
                Text(
                    text = stringResource(stageLabelRes(timing.stage)),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Four segments, one per stage: done segments are solid, the current one is half-filled. */
@Composable
private fun StageBar(segments: List<StageSegment>) {
    Row(horizontalArrangement = Arrangement.spacedBy(SEGMENT_GAP)) {
        segments.forEach { segment ->
            val fill = when (segment) {
                StageSegment.DONE -> MaterialTheme.colorScheme.primary
                StageSegment.CURRENT -> MaterialTheme.colorScheme.primary.copy(alpha = CURRENT_SEGMENT_ALPHA)
                StageSegment.PENDING -> MaterialTheme.colorScheme.surfaceVariant
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(SEGMENT_HEIGHT)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(fill),
            )
        }
    }
}

/** Elapsed time: the final duration once finished, otherwise the time since the build started. */
private fun elapsedText(build: BuildRecord, now: Instant): String? {
    if (build.isTerminal) return buildDurationText(build)
    val start = build.runningOn ?: build.initializingOn ?: build.createdOn
    return formatDuration(Duration.between(start, now).toMillis().coerceAtLeast(0L))
}

@Composable
private fun ActionRow(
    state: BuildDetailUiState,
    build: BuildRecord,
    onWatch: () -> Unit,
    onStopWatching: () -> Unit,
    onCancel: () -> Unit,
) {
    val showWatch = !build.isTerminal
    val showCancel = state.canCancel
    if (!showWatch && !showCancel) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showWatch) {
            if (state.isWatched) {
                FilledTonalButton(onClick = onStopWatching, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.builds_action_stop_watching))
                }
            } else {
                Button(onClick = onWatch, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.builds_action_watch))
                }
            }
        }
        if (showCancel) {
            OutlinedButton(
                onClick = onCancel,
                enabled = !state.isCancelling,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = OUTLINE_ALPHA)),
            ) {
                Text(stringResource(R.string.builds_action_cancel_build))
            }
        }
    }
}

private fun detailRows(build: BuildRecord): List<@Composable () -> Unit> = listOf(
    { KeyValueRow(key = stringResource(R.string.builds_field_branch), value = build.branch ?: stringResource(R.string.builds_value_none)) },
    {
        KeyValueRow(
            key = stringResource(R.string.builds_field_commit),
            value = build.commitHash ?: stringResource(R.string.builds_value_none),
            monospaceValue = true,
        )
    },
    { KeyValueRow(key = stringResource(R.string.builds_field_author), value = build.author ?: stringResource(R.string.builds_value_none)) },
)

/** One line of the log. The first and last lines round the panel's top and bottom corners. */
@Composable
private fun LogLine(text: String, index: Int, isFirst: Boolean, isLast: Boolean) {
    val shape = segmentedShape(
        isFirst = isFirst,
        isLast = isLast,
        outer = MaterialTheme.shapes.small.topStart,
        inner = ZeroCornerSize,
    )
    Text(
        text = text,
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(horizontal = LOG_PADDING, vertical = 2.dp)
            .testTag("$LOG_LINE_TAG_PREFIX$index"),
    )
}

@Composable
private fun LogFooter(state: BuildDetailUiState) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        if (state.logsFinished) {
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.builds_log_finished), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (state.logLines.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = {
                    copyLog(context, state.logLines)?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                }) { Text(stringResource(R.string.builds_copy_log)) }
                TextButton(onClick = { shareLog(context, state.logLines) }) {
                    Text(stringResource(R.string.builds_share_log))
                }
            }
        }
    }
}

/**
 * Runs [onGranted] once the user is ready to watch. The first tap explains why notifications are wanted, then asks
 * for the permission. Watching starts either way: a denied permission only makes the notifications silent.
 */
@Composable
private fun rememberWatchGate(onGranted: () -> Unit): () -> Unit {
    val context = LocalContext.current
    var showRationale by remember { mutableStateOf(false) }
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { onGranted() }
    if (showRationale) {
        ConfirmDialog(
            title = stringResource(R.string.builds_notifications_rationale_title),
            message = stringResource(R.string.builds_notifications_rationale_message),
            confirmLabel = stringResource(R.string.builds_notifications_rationale_allow),
            dismissLabel = stringResource(R.string.builds_notifications_rationale_skip),
            onConfirm = {
                showRationale = false
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            onDismiss = {
                showRationale = false
                onGranted()
            },
        )
    }
    return {
        if (needsNotificationPermission(context)) showRationale = true else onGranted()
    }
}

private fun needsNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

/** Test tags for the screen's scrolling list and its log lines, so flow tests find a line by its position. */
internal const val DETAIL_LIST_TAG = "build-detail-list"
internal const val LOG_LINE_TAG_PREFIX = "build-log-line-"

/**
 * Scroll distance that carries the log to its end. A list stops at its last item when scrolled past it, so a distance
 * this large lands the newest line at the bottom of the screen, where scrolling to the footer would leave it above.
 */
private const val SCROLL_TO_LOG_END_PX = 1_000_000f

private val PAGE_GUTTER = 16.dp
private val JUMP_BOTTOM_PADDING = 16.dp
private val SECTION_GAP = 10.dp
private val CARD_PADDING = 14.dp
private val SEGMENT_GAP = 4.dp
private val SEGMENT_HEIGHT = 5.dp
private const val CURRENT_SEGMENT_ALPHA = 0.5f
private const val OUTLINE_ALPHA = 0.6f
private val LOG_PADDING = 12.dp
