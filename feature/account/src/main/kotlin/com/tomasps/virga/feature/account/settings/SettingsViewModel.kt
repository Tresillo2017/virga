package com.tomasps.virga.feature.account.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomasps.virga.core.auth.session.SessionRepository
import com.tomasps.virga.core.ui.components.TimeRange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The one dialog the settings screen may show at a time. */
enum class SettingsDialog { SignOut, Reset, TimeRange }

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = false,
    val pureBlack: Boolean = false,
    val blockScreenshots: Boolean = false,
    val appLock: Boolean = false,
    val defaultTimeRange: TimeRange = TimeRange.TWENTY_FOUR_HOURS,
    val activeDialog: SettingsDialog? = null,
) {
    val isSignOutConfirmVisible: Boolean get() = activeDialog == SettingsDialog.SignOut
    val isResetConfirmVisible: Boolean get() = activeDialog == SettingsDialog.Reset
    val isTimeRangeDialogVisible: Boolean get() = activeDialog == SettingsDialog.TimeRange
}

class SettingsViewModel(
    private val session: SessionRepository,
    private val themes: ThemeRepository,
) : ViewModel() {

    private val activeDialog = MutableStateFlow<SettingsDialog?>(null)

    val state: StateFlow<SettingsUiState> = combine(themes.preferences, activeDialog) { prefs, dialog ->
        SettingsUiState(
            themeMode = prefs.themeMode,
            dynamicColor = prefs.dynamicColor,
            pureBlack = prefs.pureBlack,
            blockScreenshots = prefs.blockScreenshots,
            appLock = prefs.appLock,
            defaultTimeRange = prefs.defaultTimeRange,
            activeDialog = dialog,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    fun onThemeSelected(mode: ThemeMode) = save { themes.set(mode) }

    fun onDynamicColorChanged(enabled: Boolean) = save { themes.setDynamicColor(enabled) }

    fun onPureBlackChanged(enabled: Boolean) = save { themes.setPureBlack(enabled) }

    fun onBlockScreenshotsChanged(enabled: Boolean) = save { themes.setBlockScreenshots(enabled) }

    fun onAppLockChanged(enabled: Boolean) = save { themes.setAppLock(enabled) }

    fun onTimeRangeRequested() = showDialog(SettingsDialog.TimeRange)

    fun onTimeRangeDismissed() = hideDialog()

    fun onTimeRangeSelected(range: TimeRange) {
        hideDialog()
        save { themes.setDefaultTimeRange(range) }
    }

    fun onResetRequested() = showDialog(SettingsDialog.Reset)

    fun onResetDismissed() = hideDialog()

    fun onResetConfirmed() {
        hideDialog()
        save { themes.resetAll() }
    }

    fun onSignOutRequested() = showDialog(SettingsDialog.SignOut)

    fun onSignOutDismissed() = hideDialog()

    fun onSignOutConfirmed() {
        hideDialog()
        viewModelScope.launch { session.signOut() }
    }

    private fun save(write: suspend () -> Unit) {
        viewModelScope.launch { write() }
    }

    private fun showDialog(dialog: SettingsDialog) {
        activeDialog.update { dialog }
    }

    private fun hideDialog() {
        activeDialog.update { null }
    }
}
