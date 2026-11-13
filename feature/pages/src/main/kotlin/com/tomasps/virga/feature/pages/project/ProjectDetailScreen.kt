package com.tomasps.virga.feature.pages.project

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.navigation.LocalNavigator
import com.tomasps.virga.core.ui.components.KeyValueRow
import com.tomasps.virga.core.ui.components.SectionCard
import com.tomasps.virga.core.ui.components.VirgaTabRow
import com.tomasps.virga.core.ui.scaffold.ScreenScaffold
import com.tomasps.virga.core.ui.state.UiStateContent
import com.tomasps.virga.core.ui.state.asString
import com.tomasps.virga.feature.pages.R
import com.tomasps.virga.feature.pages.data.PagesProject
import com.tomasps.virga.feature.pages.deployments.DeploymentsTab
import com.tomasps.virga.feature.pages.domains.DomainsTab
import com.tomasps.virga.feature.pages.ui.PagesNote
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private const val TAB_DEPLOYMENTS = 0
private const val TAB_DOMAINS = 1
private const val TAB_SETTINGS = 2

/** Project detail: a header with the project's main facts, then three tabs. The header's state drives Settings. */
@Composable
fun ProjectDetailRoute(projectName: String) {
    val viewModel: ProjectDetailViewModel = koinViewModel(parameters = { parametersOf(projectName) })
    val state by viewModel.state.collectAsState()
    val navigator = LocalNavigator.current
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(TAB_DEPLOYMENTS) }
    val messageText = state.message?.asString()

    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            viewModel.dismissMessage()
        }
    }
    LaunchedEffect(state.deleted) {
        if (state.deleted) navigator.back()
    }

    ScreenScaffold(
        title = projectName,
        onBack = { navigator.back() },
        snackbarHostState = snackbar,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            UiStateContent(state = state.project, onRetry = viewModel::refresh) { project ->
                ProjectHeader(project)
            }
            PagesNote(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            VirgaTabRow(
                tabs = listOf(
                    stringResource(R.string.pages_tab_deployments),
                    stringResource(R.string.pages_tab_domains),
                    stringResource(R.string.pages_tab_settings),
                ),
                selectedIndex = tab,
                onSelect = { tab = it },
            )
            when (tab) {
                TAB_DEPLOYMENTS -> DeploymentsTab(projectName)
                TAB_DOMAINS -> DomainsTab(projectName)
                TAB_SETTINGS -> ProjectSettingsTab(state = state, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun ProjectHeader(project: PagesProject) {
    SectionCard(
        rows = listOf(
            {
                KeyValueRow(
                    key = stringResource(R.string.pages_header_subdomain),
                    value = project.subdomain.orEmpty().ifBlank { stringResource(R.string.pages_value_none) },
                    monospaceValue = true,
                )
            },
            {
                KeyValueRow(
                    key = stringResource(R.string.pages_header_production_branch),
                    value = project.productionBranch.orEmpty().ifBlank { stringResource(R.string.pages_value_none) },
                )
            },
        ),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
