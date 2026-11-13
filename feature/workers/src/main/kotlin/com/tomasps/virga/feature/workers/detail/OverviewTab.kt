package com.tomasps.virga.feature.workers.detail

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.tomasps.virga.core.common.builds.BuildOutcome
import com.tomasps.virga.core.common.builds.BuildSummary
import com.tomasps.virga.core.common.formatDuration
import com.tomasps.virga.core.common.formatIsoDate
import com.tomasps.virga.core.navigation.BuildDetail
import com.tomasps.virga.core.navigation.LocalNavigator
import com.tomasps.virga.core.navigation.Navigator
import com.tomasps.virga.core.navigation.WorkerBuilds
import com.tomasps.virga.core.ui.components.ListRow
import com.tomasps.virga.core.ui.components.OpenInDashboardRow
import com.tomasps.virga.core.ui.components.SectionCard
import com.tomasps.virga.core.ui.components.SectionHeader
import com.tomasps.virga.core.ui.components.StatusDot
import com.tomasps.virga.core.ui.components.StatusTone
import com.tomasps.virga.feature.workers.R
import com.tomasps.virga.feature.workers.data.BindingTarget
import com.tomasps.virga.feature.workers.data.Deployment
import com.tomasps.virga.feature.workers.data.WorkerBinding
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private const val RECENT_DEPLOYMENT_COUNT = 3
private const val SHORT_ID_LENGTH = 8
private const val ELAPSED_TICK_MS = 1_000L
private val LIVE_CARD_CORNER = 14.dp
private val LIVE_CARD_PADDING = 14.dp
private val SECTION_GAP = 6.dp

private const val DASHBOARD_WORKERS_URL = "https://dash.cloudflare.com/?to=/:account/workers-and-pages"

/** Overview: the build in progress (or the latest build), the active deployment, bindings, domains and deployments. */
@Composable
internal fun OverviewTab(
    scriptName: String,
    snapshot: WorkerSnapshot,
    latestBuild: BuildSummary?,
    accountId: String?,
    watchedBuildIds: Set<String>,
    onWatchBuild: () -> Unit,
    onStopWatchingBuild: () -> Unit,
    onOpenBinding: (BindingTarget) -> Unit,
    onOpenDeployments: () -> Unit,
) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val names = rememberVersionNames(snapshot)
    var rationaleOpen by remember { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // The watch starts whether or not the user allowed notifications: the build is still watched, just silently.
        onWatchBuild()
    }
    val requestWatch = {
        if (isNotificationPermissionNeeded(context.hasNotificationPermission())) {
            rationaleOpen = true
        } else {
            onWatchBuild()
        }
    }
    val openBuild = latestBuild?.let { build ->
        accountId?.let { id -> { navigator.navigate(BuildDetail(id, build.buildUuid, scriptName)) } }
    }
    if (rationaleOpen) {
        WatchRationaleDialog(
            onContinue = {
                rationaleOpen = false
                notificationPermission.launch(NOTIFICATION_PERMISSION)
            },
            onNotNow = {
                rationaleOpen = false
                onWatchBuild()
            },
        )
    }
    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(SECTION_GAP),
    ) {
        if (latestBuild != null && latestBuild.isBuildingNow()) {
            item {
                SectionHeader(title = stringResource(R.string.workers_overview_building_now))
                BuildingCard(
                    build = latestBuild,
                    isWatched = latestBuild.buildUuid in watchedBuildIds,
                    onWatch = requestWatch,
                    onStopWatching = onStopWatchingBuild,
                    onViewLogs = openBuild,
                )
            }
        } else {
            item { SectionHeader(title = stringResource(R.string.workers_overview_build)) }
            item {
                SectionCard(
                    rows = buildSectionRows(latestBuild, accountId, openBuild, navigator, scriptName),
                )
            }
        }
        item { SectionHeader(title = stringResource(R.string.workers_overview_active_deployment)) }
        item {
            val active = snapshot.activeDeployment
            SectionCard(
                rows = listOf {
                    ListRow(
                        headline = active?.let { names.split(it) } ?: stringResource(R.string.workers_overview_no_deployment),
                        supporting = formatIsoDate(active?.createdOn),
                        onClick = onOpenDeployments,
                    )
                },
            )
        }
        item {
            SectionHeader(title = stringResource(R.string.workers_overview_bindings_count, snapshot.bindings.size))
            SectionCard(rows = bindingRows(snapshot.bindings, onOpenBinding))
        }
        item { SectionHeader(title = stringResource(R.string.workers_overview_domains)) }
        item { SectionCard(rows = domainRows(snapshot)) }
        item { SectionHeader(title = stringResource(R.string.workers_overview_recent_deployments)) }
        item {
            SectionCard(
                rows = recentDeploymentRows(snapshot.deployments.take(RECENT_DEPLOYMENT_COUNT), names, onOpenDeployments),
            )
        }
        item { SectionHeader(title = stringResource(R.string.workers_overview_issues)) }
        item {
            SectionCard(
                rows = listOf {
                    OpenInDashboardRow(
                        label = stringResource(R.string.workers_overview_issues),
                        description = stringResource(R.string.workers_overview_issues_description),
                        onOpen = { navigator.openInBrowser(DASHBOARD_WORKERS_URL) },
                    )
                },
            )
        }
        item { SectionHeader(title = stringResource(R.string.workers_overview_access)) }
        item {
            SectionCard(
                rows = listOf {
                    OpenInDashboardRow(
                        label = stringResource(R.string.workers_overview_access),
                        description = stringResource(R.string.workers_overview_access_description),
                        onOpen = { navigator.openInBrowser(DASHBOARD_WORKERS_URL) },
                    )
                },
            )
        }
    }
}

/** The latest build when nothing is running: a status dot, its commit, and the links to all builds. */
@Composable
private fun buildSectionRows(
    latestBuild: BuildSummary?,
    accountId: String?,
    openBuild: (() -> Unit)?,
    navigator: Navigator,
    scriptName: String,
): List<@Composable () -> Unit> {
    val rows = mutableListOf<@Composable () -> Unit>()
    if (latestBuild == null) {
        rows += { ListRow(headline = stringResource(R.string.workers_overview_no_build)) }
    } else {
        rows += {
            ListRow(
                headline = latestBuild.commitMessage ?: latestBuild.buildUuid.take(SHORT_ID_LENGTH),
                supporting = latestBuild.branch,
                leading = { StatusDot(tone = summaryTone(latestBuild)) },
                onClick = openBuild,
            )
        }
    }
    if (accountId != null) {
        rows += {
            ListRow(
                headline = stringResource(R.string.workers_overview_all_builds),
                onClick = { navigator.navigate(WorkerBuilds(accountId, scriptName)) },
            )
        }
    }
    return rows
}

/** Highlighted card for a build that is queued, initializing or running: elapsed time and the two build actions. */
@Composable
private fun BuildingCard(
    build: BuildSummary,
    isWatched: Boolean,
    onWatch: () -> Unit,
    onStopWatching: () -> Unit,
    onViewLogs: (() -> Unit)?,
) {
    val now by produceState(initialValue = Instant.now()) {
        while (true) {
            delay(ELAPSED_TICK_MS)
            value = Instant.now()
        }
    }
    val started = build.startedOn ?: build.createdOn
    val elapsed = formatDuration(Duration.between(started, now).toMillis().coerceAtLeast(0L))
    val branch = build.branch
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LIVE_CARD_CORNER))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(LIVE_CARD_CORNER))
            .padding(LIVE_CARD_PADDING),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(tone = StatusTone.Pending)
            Spacer(Modifier.width(10.dp))
            Text(
                text = branch?.let { stringResource(R.string.workers_overview_building_branch, it) }
                    ?: stringResource(R.string.workers_overview_building),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.workers_overview_elapsed, elapsed),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        build.commitMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isWatched) {
                Button(onClick = onStopWatching, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.workers_overview_stop_watching_action))
                }
            } else {
                Button(onClick = onWatch, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.workers_overview_watch_build_action))
                }
            }
            if (onViewLogs != null) {
                OutlinedButton(onClick = onViewLogs, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.workers_overview_view_logs))
                }
            }
        }
    }
}

private fun bindingRows(bindings: List<WorkerBinding>, onOpen: (BindingTarget) -> Unit): List<@Composable () -> Unit> {
    if (bindings.isEmpty()) return listOf { ListRow(headline = stringResource(R.string.workers_overview_no_bindings)) }
    return bindings.map { binding -> { BindingRow(binding = binding, onOpen = onOpen) } }
}

private fun domainRows(snapshot: WorkerSnapshot): List<@Composable () -> Unit> {
    val rows = mutableListOf<@Composable () -> Unit>()
    rows += {
        ListRow(
            headline = stringResource(
                if (snapshot.subdomain.enabled) R.string.workers_overview_workers_dev_on else R.string.workers_overview_workers_dev_off,
            ),
            supporting = stringResource(R.string.workers_overview_workers_dev_label),
        )
    }
    if (snapshot.domains.isEmpty()) {
        rows += { ListRow(headline = stringResource(R.string.workers_overview_no_custom_domains)) }
    }
    snapshot.domains.forEach { domain ->
        rows += { ListRow(headline = domain.hostname, supporting = domain.zoneName) }
    }
    return rows
}

private fun recentDeploymentRows(
    deployments: List<Deployment>,
    names: VersionNames,
    onOpen: () -> Unit,
): List<@Composable () -> Unit> = deployments.map { deployment ->
    {
        ListRow(
            headline = names.split(deployment),
            supporting = deployment.annotations?.message ?: formatIsoDate(deployment.createdOn),
            onClick = onOpen,
        )
    }
}

@Composable
private fun BindingRow(binding: WorkerBinding, onOpen: (BindingTarget) -> Unit) {
    val target = binding.target
    val typeLabel = bindingTypeLabelRes(binding.type)?.let { stringResource(it) } ?: binding.type
    ListRow(
        headline = binding.name,
        supporting = typeLabel,
        onClick = target?.let { { onOpen(it) } },
    )
}

/** Tone of a build's dot on the overview: active builds pulse, finished ones show their outcome. */
private fun summaryTone(build: BuildSummary): StatusTone = when {
    build.isBuildingNow() -> StatusTone.Pending
    build.outcome == BuildOutcome.SUCCESS -> StatusTone.Success
    build.outcome == BuildOutcome.FAIL -> StatusTone.Error
    else -> StatusTone.Neutral
}

private fun Context.hasNotificationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, NOTIFICATION_PERMISSION) == PackageManager.PERMISSION_GRANTED
