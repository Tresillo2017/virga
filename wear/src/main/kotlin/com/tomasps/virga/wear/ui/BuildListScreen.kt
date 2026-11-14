package com.tomasps.virga.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.compose.foundation.layout.Box
import com.tomasps.virga.R
import com.tomasps.virga.core.common.builds.BuildSummary
import com.tomasps.virga.wear.builds.routeKey
import com.tomasps.virga.wear.present.BuildTileModel
import com.tomasps.virga.wear.present.tileFor
import java.time.Instant

private val StatusDotSize = 10.dp

@Composable
fun BuildListScreen(
    state: ListState,
    onOpen: (BuildSummary) -> Unit,
    onRefresh: () -> Unit,
) {
    when (state) {
        ListState.Loading -> MessageScreen(stringResource(R.string.builds_loading))
        ListState.NotConnected -> ConnectScreen()
        ListState.Stale -> StaleScreen()
        ListState.Empty -> MessageScreen(stringResource(R.string.builds_empty))
        is ListState.Error -> MessageScreen(state.kind.phrase(), stringResource(R.string.refresh), onRefresh)
        is ListState.Content -> BuildRows(state.builds, onOpen)
    }
}

@Composable
private fun BuildRows(builds: List<BuildSummary>, onOpen: (BuildSummary) -> Unit) {
    val now = remember(builds) { Instant.now() }
    val listState = rememberScalingLazyListState()
    ScreenScaffold(scrollState = listState) { padding ->
        ScalingLazyColumn(state = listState, contentPadding = padding) {
            item { ListHeader { Text(stringResource(R.string.builds_title)) } }
            items(builds, key = { it.routeKey() }) { build ->
                BuildRow(build, tileFor(build, fetchedAt = now, now = now), onClick = { onOpen(build) })
            }
        }
    }
}

@Composable
private fun BuildRow(build: BuildSummary, model: BuildTileModel, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        icon = { Box(Modifier.size(StatusDotSize).background(model.state.color(), CircleShape)) },
        label = { Text(build.scriptName, maxLines = 1) },
        secondaryLabel = { Text(stringResource(R.string.status_with_age, model.state.label(), model.detail), maxLines = 1) },
    )
}
