package com.tomasps.virga.feature.account.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.tomasps.virga.core.navigation.LocalNavigator
import com.tomasps.virga.core.ui.components.ListRow
import com.tomasps.virga.core.ui.components.SectionCard
import com.tomasps.virga.core.ui.components.SectionHeader
import com.tomasps.virga.core.ui.dialogs.ConfirmDialog
import com.tomasps.virga.core.ui.scaffold.ScreenScaffold
import androidx.compose.ui.unit.dp
import com.tomasps.virga.feature.account.R
import org.koin.compose.viewmodel.koinViewModel

/** Page gutter. Each card sits inside it, and [SectionHeader] adds its own inset, so headers line up with row text. */
private val PAGE_GUTTER = 16.dp

@Composable
fun SettingsRoute(viewModel: SettingsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val navigator = LocalNavigator.current
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
            SectionHeader(title = stringResource(R.string.settings_appearance))
            SectionCard(
                rows = listOf(
                    { ThemeOption(R.string.settings_theme_system, ThemeMode.System, state.themeMode, viewModel::onThemeSelected) },
                    { ThemeOption(R.string.settings_theme_light, ThemeMode.Light, state.themeMode, viewModel::onThemeSelected) },
                    { ThemeOption(R.string.settings_theme_dark, ThemeMode.Dark, state.themeMode, viewModel::onThemeSelected) },
                ),
            )
            SectionHeader(title = stringResource(R.string.settings_session))
            SectionCard(
                rows = listOf {
                    ListRow(
                        headline = stringResource(R.string.settings_sign_out),
                        supporting = stringResource(R.string.settings_sign_out_supporting),
                        onClick = viewModel::onSignOutRequested,
                    )
                },
            )
        }
    }
    if (state.isSignOutConfirmVisible) {
        ConfirmDialog(
            title = stringResource(R.string.settings_sign_out_title),
            message = stringResource(R.string.settings_sign_out_message),
            onConfirm = viewModel::onSignOutConfirmed,
            onDismiss = viewModel::onSignOutDismissed,
            confirmLabel = stringResource(R.string.settings_sign_out_confirm),
            dismissLabel = stringResource(R.string.settings_cancel),
            destructive = true,
        )
    }
}

@Composable
private fun ThemeOption(
    labelRes: Int,
    mode: ThemeMode,
    selected: ThemeMode,
    onSelected: (ThemeMode) -> Unit,
) {
    ListRow(
        headline = stringResource(labelRes),
        trailing = { RadioButton(selected = mode == selected, onClick = null) },
        onClick = { onSelected(mode) },
    )
}
