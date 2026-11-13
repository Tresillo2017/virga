package com.tomasps.virga.feature.d1.detail.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import com.tomasps.virga.feature.d1.detail.D1Tab
import com.tomasps.virga.feature.d1.detail.console.ConsoleCallbacks
import com.tomasps.virga.feature.d1.detail.console.ConsoleTab
import com.tomasps.virga.feature.d1.detail.console.ConsoleViewModel
import com.tomasps.virga.feature.d1.detail.export.ExportCallbacks
import com.tomasps.virga.feature.d1.detail.export.ExportTab
import com.tomasps.virga.feature.d1.detail.export.ExportViewModel
import com.tomasps.virga.feature.d1.detail.metrics.MetricsCallbacks
import com.tomasps.virga.feature.d1.detail.metrics.MetricsTab
import com.tomasps.virga.feature.d1.detail.metrics.MetricsViewModel
import com.tomasps.virga.feature.d1.detail.settings.DatabaseSettingsViewModel
import com.tomasps.virga.feature.d1.detail.settings.SettingsCallbacks
import com.tomasps.virga.feature.d1.detail.settings.SettingsTab
import com.tomasps.virga.feature.d1.detail.tables.TablesCallbacks
import com.tomasps.virga.feature.d1.detail.tables.TablesTab
import com.tomasps.virga.feature.d1.detail.tables.TablesViewModel
import com.tomasps.virga.feature.d1.detail.timetravel.TimeTravelCallbacks
import com.tomasps.virga.feature.d1.detail.timetravel.TimeTravelTab
import com.tomasps.virga.feature.d1.detail.timetravel.TimeTravelViewModel
import com.tomasps.virga.feature.d1.detail.ReadReplicationMode
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Picks the tab's composable. Each tab reads its own view model, scoped to the database screen. */
@Composable
internal fun D1TabRoutes(
    tab: D1Tab,
    databaseId: String,
    onOpenDashboard: () -> Unit,
    onDeleted: () -> Unit,
) {
    when (tab) {
        D1Tab.TABLES -> TablesRoute(databaseId)
        D1Tab.CONSOLE -> ConsoleRoute(databaseId)
        D1Tab.TIME_TRAVEL -> TimeTravelRoute(databaseId)
        D1Tab.EXPORT -> ExportRoute(databaseId)
        D1Tab.SETTINGS -> SettingsRoute(databaseId, onOpenDashboard, onDeleted)
        D1Tab.METRICS -> MetricsRoute(databaseId, onOpenDashboard)
    }
}

@Composable
private fun TablesRoute(databaseId: String) {
    val viewModel: TablesViewModel = koinViewModel(parameters = { parametersOf(databaseId) })
    val state by viewModel.state.collectAsState()
    TablesTab(
        state = state,
        callbacks = TablesCallbacks(
            onOpenTable = viewModel::openTable,
            onCloseTable = viewModel::closeTable,
            onRefreshTables = viewModel::refreshTables,
            onReloadRows = viewModel::reloadRows,
            onLoadMoreRows = viewModel::loadMoreRows,
            onSelectRow = viewModel::selectRow,
            onBeginInsert = viewModel::beginInsert,
            onBeginEdit = viewModel::beginEdit,
            onRequestDelete = viewModel::requestDelete,
            onCellText = viewModel::onCellText,
            onCellNull = viewModel::onCellNull,
            onDismissEditor = viewModel::dismissEditor,
            onReviewEditor = viewModel::reviewEditor,
            onCancelWrite = viewModel::cancelWrite,
            onConfirmWrite = viewModel::confirmWrite,
            onDismissActionError = viewModel::dismissActionError,
        ),
    )
}

@Composable
private fun ConsoleRoute(databaseId: String) {
    val viewModel: ConsoleViewModel = koinViewModel(parameters = { parametersOf(databaseId) })
    val state by viewModel.state.collectAsState()
    ConsoleTab(
        state = state,
        callbacks = ConsoleCallbacks(
            onSqlChanged = viewModel::onSqlChanged,
            onRun = viewModel::run,
            onConfirmRun = viewModel::confirmRun,
            onCancelRun = viewModel::cancelRun,
            onUseHistory = viewModel::useHistory,
            onClearHistory = viewModel::clearHistory,
            onDismissError = viewModel::dismissError,
        ),
    )
}

@Composable
private fun TimeTravelRoute(databaseId: String) {
    val viewModel: TimeTravelViewModel = koinViewModel(parameters = { parametersOf(databaseId) })
    val state by viewModel.state.collectAsState()
    TimeTravelTab(
        state = state,
        callbacks = TimeTravelCallbacks(
            onRefresh = viewModel::refresh,
            onLookupChanged = viewModel::onLookupChanged,
            onLookUp = viewModel::lookUp,
            onRestoreBookmark = viewModel::requestRestoreToBookmark,
            onRestoreTimestamp = viewModel::requestRestoreToTimestamp,
            onCancelRestore = viewModel::cancelRestore,
            onConfirmRestore = viewModel::confirmRestore,
            onUndo = viewModel::requestUndo,
            onDismissActionError = viewModel::dismissActionError,
        ),
    )
}

@Composable
private fun ExportRoute(databaseId: String) {
    val viewModel: ExportViewModel = koinViewModel(parameters = { parametersOf(databaseId) })
    val state by viewModel.state.collectAsState()
    ExportTab(
        state = state,
        callbacks = ExportCallbacks(
            onStructureOnly = viewModel::setStructureOnly,
            onDataOnly = viewModel::setDataOnly,
            onStart = viewModel::start,
            onStop = viewModel::stop,
            onDismiss = viewModel::dismiss,
        ),
    )
}

@Composable
private fun SettingsRoute(databaseId: String, onOpenDashboard: () -> Unit, onDeleted: () -> Unit) {
    val viewModel: DatabaseSettingsViewModel = koinViewModel(parameters = { parametersOf(databaseId) })
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.isDeleted) {
        if (state.isDeleted) onDeleted()
    }
    SettingsTab(
        state = state,
        callbacks = SettingsCallbacks(
            onRetry = viewModel::retry,
            onSetReplication = { mode: ReadReplicationMode -> viewModel.setReadReplication(mode) },
            onRequestDelete = viewModel::requestDelete,
            onCancelDelete = viewModel::cancelDelete,
            onConfirmDelete = viewModel::confirmDelete,
            onDismissActionError = viewModel::dismissActionError,
            onOpenDashboard = onOpenDashboard,
        ),
    )
}

@Composable
private fun MetricsRoute(databaseId: String, onOpenDashboard: () -> Unit) {
    val viewModel: MetricsViewModel = koinViewModel(parameters = { parametersOf(databaseId) })
    val state by viewModel.state.collectAsState()
    MetricsTab(
        state = state,
        callbacks = MetricsCallbacks(onRetry = viewModel::retry, onOpenInsights = onOpenDashboard),
    )
}
