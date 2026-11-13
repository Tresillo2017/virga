package com.tomasps.virga.feature.d1.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.collectAsState
import com.tomasps.virga.core.navigation.LocalNavigator
import com.tomasps.virga.core.ui.components.VirgaTabRow
import com.tomasps.virga.core.ui.scaffold.ScreenScaffold
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.core.ui.state.ErrorState
import com.tomasps.virga.core.navigation.D1Database as D1DatabaseKey
import com.tomasps.virga.feature.d1.R
import com.tomasps.virga.feature.d1.detail.tabs.D1TabRoutes
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** A database screen: a header with the database name, then one tab at a time. Each tab owns its view model. */
@Composable
internal fun D1DetailRoute(key: D1DatabaseKey) {
    val databaseId = key.databaseId
    val viewModel: D1DetailViewModel = koinViewModel(parameters = { parametersOf(databaseId) })
    val state by viewModel.state.collectAsState()
    val navigator = LocalNavigator.current
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val title = (state.info as? UiState.Content)?.data?.name ?: stringResource(R.string.d1_title_database)
    ScreenScaffold(
        title = title,
        onBack = { navigator.back() },
        onRefresh = viewModel::retry,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            (state.info as? UiState.Failure)?.let { failure ->
                ErrorState(error = failure.error, onRetry = viewModel::retry)
            }
            VirgaTabRow(
                tabs = D1Tab.entries.map { stringResource(it.labelRes) },
                selectedIndex = selected,
                onSelect = { selected = it },
            )
            Box(Modifier.weight(1f)) {
                D1TabRoutes(
                    tab = D1Tab.entries[selected],
                    databaseId = databaseId,
                    onOpenDashboard = { navigator.openInBrowser(DASHBOARD_URL) },
                    onDeleted = { navigator.back() },
                )
            }
        }
    }
}

private const val DASHBOARD_URL = "https://dash.cloudflare.com/"
