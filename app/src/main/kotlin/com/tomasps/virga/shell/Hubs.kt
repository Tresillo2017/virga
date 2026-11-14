package com.tomasps.virga.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tomasps.virga.R
import com.tomasps.virga.core.navigation.D1Root
import com.tomasps.virga.core.navigation.KvRoot
import com.tomasps.virga.core.navigation.LocalNavigator
import androidx.navigation3.runtime.NavKey
import com.tomasps.virga.core.navigation.PagesRoot
import com.tomasps.virga.core.navigation.R2Root
import com.tomasps.virga.core.navigation.WorkersRoot
import com.tomasps.virga.core.ui.components.ListRow
import com.tomasps.virga.core.ui.components.SectionCard
import com.tomasps.virga.core.ui.scaffold.ScreenScaffold

private data class HubRow(
    val label: Int,
    val supporting: Int,
    val key: NavKey,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

/** Compute hub: entry points into Workers and Pages. Drawn by the shell; the lists belong to the features. */
@Composable
fun ComputeHubRoute() {
    HubScreen(
        title = stringResource(R.string.hub_compute_title),
        rows = listOf(
            HubRow(R.string.hub_compute_workers, R.string.hub_compute_workers_supporting, WorkersRoot, Icons.Filled.Memory),
            HubRow(R.string.hub_compute_pages, R.string.hub_compute_pages_supporting, PagesRoot, Icons.Filled.Folder),
        ),
    )
}

/** Storage hub: entry points into R2, D1 and KV. */
@Composable
fun StorageHubRoute() {
    HubScreen(
        title = stringResource(R.string.hub_storage_title),
        rows = listOf(
            HubRow(R.string.hub_storage_r2, R.string.hub_storage_r2_supporting, R2Root, Icons.Filled.Storage),
            HubRow(R.string.hub_storage_d1, R.string.hub_storage_d1_supporting, D1Root, Icons.Filled.TableChart),
            HubRow(R.string.hub_storage_kv, R.string.hub_storage_kv_supporting, KvRoot, Icons.Filled.Dns),
        ),
    )
}

@Composable
private fun HubScreen(title: String, rows: List<HubRow>) {
    ScreenScaffold(title = title) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            SectionCard(rows = rows.map { row -> @Composable { HubListRow(row) } })
        }
    }
}

@Composable
private fun HubListRow(row: HubRow) {
    val navigator = LocalNavigator.current
    ListRow(
        headline = stringResource(row.label),
        supporting = stringResource(row.supporting),
        leading = { Icon(row.icon, contentDescription = null) },
        trailing = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
        onClick = { navigator.navigate(row.key) },
    )
}
