package com.tomasps.virga.feature.account.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.navigation.LocalNavigator
import com.tomasps.virga.core.ui.scaffold.ScreenScaffold
import com.tomasps.virga.feature.account.AccountAbout
import com.tomasps.virga.feature.account.R
import org.koin.compose.viewmodel.koinViewModel

/** Page gutter. Cards sit inside it; section headers add their own inset, so they line up with row text. */
private val PAGE_GUTTER = 16.dp

/** Route entry point: wires the ViewModel, the navigator and the device facts to the settings sections. */
@Composable
fun SettingsRoute(viewModel: SettingsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val facts = remember(context) { deviceFacts(context) }
    ScreenScaffold(
        title = stringResource(R.string.settings_title),
        onBack = { navigator.back() },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = PAGE_GUTTER)
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsSections(
                state = state,
                facts = facts,
                viewModel = viewModel,
                onAboutClick = { navigator.navigate(AccountAbout) },
                onNotificationsClick = { openNotificationSettings(context) },
            )
        }
    }
    SettingsDialogs(state = state, viewModel = viewModel)
}
