package com.tomasps.virga.feature.account.settings

import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Update
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import com.tomasps.virga.core.ui.components.ListRow
import com.tomasps.virga.core.ui.components.SectionCard
import com.tomasps.virga.core.ui.components.SectionHeader
import com.tomasps.virga.core.ui.components.TimeRange
import com.tomasps.virga.feature.account.R

/**
 * A section header read as one heading. The header's text and its heading role sit on one node, so a test or a screen
 * reader finds the title as a heading rather than the text alone.
 */
@Composable
private fun SettingsHeader(title: String) {
    SectionHeader(
        title = title,
        modifier = Modifier.semantics(mergeDescendants = true) {},
    )
}

/**
 * The settings sections in reading order. Each one is a header over a [SectionCard]; sign out sits last, on its own.
 */
@Composable
fun SettingsSections(
    state: SettingsUiState,
    facts: DeviceFacts,
    viewModel: SettingsViewModel,
    onAboutClick: () -> Unit,
    onNotificationsClick: () -> Unit,
) {
    AppearanceSection(state, viewModel)
    AnalyticsSection(state.defaultTimeRange, viewModel::onTimeRangeRequested)
    PrivacySection(state, facts.isDeviceSecure, viewModel)
    NotificationsSection(onNotificationsClick)
    AboutSection(facts.versionName, onAboutClick, viewModel::onResetRequested)
    SessionSection(viewModel::onSignOutRequested)
}

@Composable
private fun AppearanceSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    val themeChoices = listOf(
        ThemeMode.System to stringResource(R.string.settings_theme_system),
        ThemeMode.Light to stringResource(R.string.settings_theme_light),
        ThemeMode.Dark to stringResource(R.string.settings_theme_dark),
    )
    val themeRow: @Composable () -> Unit = {
        ThemeChoiceGroup(
            selected = state.themeMode,
            choices = themeChoices,
            onSelected = viewModel::onThemeSelected,
        )
    }
    val dynamicColorRow: @Composable () -> Unit = {
        SettingsSwitchRow(
            label = stringResource(R.string.settings_dynamic_color),
            supporting = stringResource(R.string.settings_dynamic_color_supporting),
            checked = state.dynamicColor,
            onCheckedChange = viewModel::onDynamicColorChanged,
            icon = Icons.Outlined.Palette,
        )
    }
    val pureBlackRow: @Composable () -> Unit = {
        SettingsSwitchRow(
            label = stringResource(R.string.settings_pure_black),
            supporting = stringResource(R.string.settings_pure_black_supporting),
            checked = state.pureBlack,
            onCheckedChange = viewModel::onPureBlackChanged,
            icon = Icons.Outlined.DarkMode,
        )
    }
    SettingsHeader(title = stringResource(R.string.settings_appearance))
    SectionCard(rows = appearanceRows(themeRow, dynamicColorRow, pureBlackRow))
}

/** Dynamic colour needs Android 12 (API 31) for wallpaper colours, so the switch only appears from there. */
private fun appearanceRows(
    themeRow: @Composable () -> Unit,
    dynamicColorRow: @Composable () -> Unit,
    pureBlackRow: @Composable () -> Unit,
): List<@Composable () -> Unit> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    listOf(themeRow, dynamicColorRow, pureBlackRow)
} else {
    listOf(themeRow, pureBlackRow)
}

@Composable
private fun AnalyticsSection(range: TimeRange, onRangeClick: () -> Unit) {
    SettingsHeader(title = stringResource(R.string.settings_analytics))
    SectionCard(
        rows = listOf {
            ListRow(
                headline = stringResource(R.string.settings_default_range),
                supporting = stringResource(range.labelResId),
                leading = { SettingsIcon(Icons.Outlined.Schedule) },
                onClick = onRangeClick,
            )
        },
    )
}

@Composable
private fun PrivacySection(state: SettingsUiState, isDeviceSecure: Boolean, viewModel: SettingsViewModel) {
    SettingsHeader(title = stringResource(R.string.settings_privacy))
    SectionCard(
        rows = listOf(
            {
                SettingsSwitchRow(
                    label = stringResource(R.string.settings_block_screenshots),
                    supporting = stringResource(R.string.settings_block_screenshots_supporting),
                    checked = state.blockScreenshots,
                    onCheckedChange = viewModel::onBlockScreenshotsChanged,
                    icon = Icons.Outlined.Block,
                )
            },
            {
                SettingsSwitchRow(
                    label = stringResource(R.string.settings_app_lock),
                    supporting = stringResource(
                        if (isDeviceSecure) R.string.settings_app_lock_supporting else R.string.settings_app_lock_unavailable,
                    ),
                    checked = state.appLock,
                    onCheckedChange = viewModel::onAppLockChanged,
                    icon = Icons.Outlined.Lock,
                    enabled = isDeviceSecure,
                )
            },
        ),
    )
}

@Composable
private fun NotificationsSection(onNotificationsClick: () -> Unit) {
    SettingsHeader(title = stringResource(R.string.settings_notifications))
    SectionCard(
        rows = listOf {
            ListRow(
                headline = stringResource(R.string.settings_notifications),
                supporting = stringResource(R.string.settings_notifications_supporting),
                leading = { SettingsIcon(Icons.Outlined.Notifications) },
                onClick = onNotificationsClick,
            )
        },
    )
}

@Composable
private fun AboutSection(versionName: String, onAboutClick: () -> Unit, onResetClick: () -> Unit) {
    SettingsHeader(title = stringResource(R.string.settings_about))
    SectionCard(
        rows = listOf(
            {
                ListRow(
                    headline = stringResource(R.string.account_about),
                    leading = { SettingsIcon(Icons.Outlined.Info) },
                    onClick = onAboutClick,
                )
            },
            {
                ListRow(
                    headline = stringResource(R.string.settings_version),
                    supporting = versionName,
                    leading = { SettingsIcon(Icons.Outlined.Update) },
                )
            },
            {
                ListRow(
                    headline = stringResource(R.string.settings_reset),
                    supporting = stringResource(R.string.settings_reset_supporting),
                    leading = { SettingsIcon(Icons.Outlined.Restore) },
                    onClick = onResetClick,
                )
            },
        ),
    )
}

/** No leading icon here on purpose: the header text lines up with the sign-out text it sits above. */
@Composable
private fun SessionSection(onSignOutClick: () -> Unit) {
    SettingsHeader(title = stringResource(R.string.settings_session))
    SectionCard(
        rows = listOf {
            ListRow(
                headline = stringResource(R.string.settings_sign_out),
                supporting = stringResource(R.string.settings_sign_out_supporting),
                onClick = onSignOutClick,
            )
        },
    )
}
