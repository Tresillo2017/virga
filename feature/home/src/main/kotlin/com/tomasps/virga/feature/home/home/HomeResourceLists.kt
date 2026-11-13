package com.tomasps.virga.feature.home.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.tomasps.virga.core.common.builds.BuildSummary
import com.tomasps.virga.core.common.formatCompactCount
import com.tomasps.virga.core.navigation.DomainsRoot
import com.tomasps.virga.core.navigation.WorkerDetail
import com.tomasps.virga.core.navigation.WorkersRoot
import com.tomasps.virga.core.navigation.ZoneDetail
import com.tomasps.virga.core.ui.components.ListRow
import com.tomasps.virga.core.ui.components.SectionCard
import com.tomasps.virga.core.ui.components.SectionHeader
import com.tomasps.virga.core.ui.components.StatusDot
import com.tomasps.virga.core.ui.components.StatusTone
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.feature.home.R
import com.tomasps.virga.feature.home.analytics.HomeAnalytics
import com.tomasps.virga.feature.home.analytics.ScriptRequests
import com.tomasps.virga.feature.home.search.SearchSource
import com.tomasps.virga.feature.home.search.SourceState

private val NOTE_PADDING_HORIZONTAL = 16.dp

/**
 * The Workers and Domains cards. Each lists the loaded resources, busiest first, with its request count when the
 * analytics has one. A row shows a status dot only where the API gives a status: a Worker with a build running, or a
 * zone's status.
 */
@Composable
internal fun HomeResourceLists(state: HomeUiState, actions: HomeActions) {
    val analytics = (state.analytics as? UiState.Content<HomeAnalytics>)?.data
    val buildingScripts = (state.building as? UiState.Content<List<BuildSummary>>)?.data.orEmpty()
        .map { it.scriptName }
        .toSet()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WorkersCard(
            source = state.search.sources[SearchSource.WORKERS],
            requestsByWorker = analytics?.requestsByWorker.orEmpty(),
            countsKnown = analytics != null && analytics.workersIssue == null,
            buildingScripts = buildingScripts,
            onOpen = actions.onOpen,
        )
        DomainsCard(
            source = state.search.sources[SearchSource.ZONES],
            requestsByZone = analytics?.requestsByZone.orEmpty(),
            onOpen = actions.onOpen,
        )
    }
}

@Composable
private fun WorkersCard(
    source: SourceState?,
    requestsByWorker: List<ScriptRequests>,
    countsKnown: Boolean,
    buildingScripts: Set<String>,
    onOpen: (NavKey) -> Unit,
) {
    val loaded = (source as? SourceState.Loaded)?.items
    ResourceHeader(
        titleRes = R.string.home_section_workers,
        count = loaded?.size,
        seeAll = WorkersRoot,
        onOpen = onOpen,
    )
    when (source) {
        is SourceState.Unavailable -> ResourceNote(sourceIssueText(SearchSource.WORKERS, source.reason))
        is SourceState.Loaded -> if (loaded.isNullOrEmpty()) {
            ResourceNote(stringResource(R.string.home_workers_none))
        } else {
            val rows = workerRows(loaded, requestsByWorker, buildingScripts).take(SHORTCUT_LIMIT)
            SectionCard(
                rows = rows.map { row -> { WorkerListRow(row, countsKnown) { onOpen(WorkerDetail(scriptName = row.name)) } } },
            )
        }
        else -> LoadingNote()
    }
}

@Composable
private fun DomainsCard(
    source: SourceState?,
    requestsByZone: Map<String, Long>,
    onOpen: (NavKey) -> Unit,
) {
    val loaded = (source as? SourceState.Loaded)?.items
    ResourceHeader(
        titleRes = R.string.home_section_domains,
        count = loaded?.size,
        seeAll = DomainsRoot,
        onOpen = onOpen,
    )
    when (source) {
        is SourceState.Unavailable -> ResourceNote(sourceIssueText(SearchSource.ZONES, source.reason))
        is SourceState.Loaded -> if (loaded.isNullOrEmpty()) {
            ResourceNote(stringResource(R.string.home_domains_none))
        } else {
            val rows = zoneRows(loaded, requestsByZone).take(SHORTCUT_LIMIT)
            SectionCard(rows = rows.map { row -> { DomainListRow(row) { onOpen(ZoneDetail(zoneId = row.id)) } } })
        }
        else -> LoadingNote()
    }
}

/** A section header that shows the count once the list has loaded, and a "See all" action when there is something to see. */
@Composable
private fun ResourceHeader(titleRes: Int, count: Int?, seeAll: NavKey, onOpen: (NavKey) -> Unit) {
    val title = stringResource(titleRes)
    val headerText = if (count == null) title else stringResource(R.string.home_section_with_count, title, count)
    SectionHeader(
        title = headerText,
        action = if (count != null && count > 0) {
            { TextButton(onClick = { onOpen(seeAll) }) { Text(stringResource(R.string.home_see_all)) } }
        } else {
            null
        },
    )
}

/**
 * A Worker row. Its count is a fact only when analytics has loaded: while analytics is loading or failed, the row
 * shows no count, so a missing number is never read as "no requests".
 */
@Composable
private fun WorkerListRow(row: WorkerRow, countsKnown: Boolean, onClick: () -> Unit) {
    val supporting: String? = when {
        row.requests != null -> stringResource(R.string.home_shortcut_requests, formatCompactCount(row.requests))
        countsKnown -> stringResource(R.string.home_no_traffic)
        else -> null
    }
    val leading: (@Composable () -> Unit)? = if (row.isBuilding) {
        { StatusDot(tone = StatusTone.Pending, contentDescription = stringResource(R.string.home_worker_building)) }
    } else {
        null
    }
    ListRow(headline = row.name, supporting = supporting, leading = leading, onClick = onClick)
}

/**
 * A domain row. The status dot and the status word appear only when the zone has a status Home knows. The request
 * count appears only when analytics has one for the zone.
 */
@Composable
private fun DomainListRow(row: ZoneRow, onClick: () -> Unit) {
    val statusRes = zoneStatusLabelRes(row.status)
    val statusLabel = statusRes?.let { stringResource(it) }
    val supporting: String? = when {
        statusLabel != null && row.requests != null ->
            stringResource(R.string.home_domain_status_requests, statusLabel, formatCompactCount(row.requests))
        statusLabel != null -> statusLabel
        row.requests != null -> stringResource(R.string.home_shortcut_requests, formatCompactCount(row.requests))
        else -> null
    }
    val tone = zoneStatusTone(row.status)
    val leading: (@Composable () -> Unit)? = if (tone != null) {
        { StatusDot(tone = tone, contentDescription = statusLabel) }
    } else {
        null
    }
    ListRow(headline = row.name, supporting = supporting, leading = leading, onClick = onClick)
}

@Composable
private fun ResourceNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = NOTE_PADDING_HORIZONTAL, vertical = 8.dp),
    )
}

@Composable
private fun LoadingNote() {
    Row(
        modifier = Modifier.padding(horizontal = NOTE_PADDING_HORIZONTAL, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), color = MaterialTheme.colorScheme.primary)
    }
}
