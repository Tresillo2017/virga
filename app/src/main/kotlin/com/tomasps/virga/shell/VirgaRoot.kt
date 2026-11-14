package com.tomasps.virga.shell

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import com.tomasps.virga.core.auth.session.SessionRepository
import com.tomasps.virga.core.auth.session.SessionState
import com.tomasps.virga.core.navigation.PendingNavigation
import com.tomasps.virga.core.ui.brand.DarkSplash
import com.tomasps.virga.core.ui.brand.SplashGate
import com.tomasps.virga.core.ui.theme.VirgaTheme
import com.tomasps.virga.core.ui.window.SecureWindow
import com.tomasps.virga.core.ui.window.findActivity
import com.tomasps.virga.feature.account.settings.ThemeMode
import com.tomasps.virga.feature.account.settings.ThemeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

/**
 * Top of the Compose tree: the one-time animated splash, then the session-dependent shell.
 *
 * The splash is drawn at once, before the session or the saved theme is read. Both are only needed after the
 * animation, so reading them does not hold back the first frame. [sessionProvider] and [themesProvider] are
 * called only at that point: building them is not free, and doing it during the first composition would stall the
 * system splash. The splash is always dark, matching the system splash, so the hand-off looks the same in light
 * and dark mode. While the session is restoring or the theme is unknown, the splash holds its final frame.
 */
@Composable
fun VirgaRoot(
    sessionProvider: () -> SessionRepository,
    themesProvider: () -> ThemeRepository,
    pending: PendingNavigation,
    splashVisible: StateFlow<Boolean>,
    onSplashStarted: () -> Unit,
) {
    SplashGate(splashVisible, onSplashStarted) {
        AfterSplash(sessionProvider, themesProvider, pending)
    }
}

@Composable
private fun AfterSplash(
    sessionProvider: () -> SessionRepository,
    themesProvider: () -> ThemeRepository,
    pending: PendingNavigation,
) {
    // Building the graph behind these takes long enough to stall the main thread, so it happens off it. The splash
    // keeps drawing its final frame until both are ready.
    val session by produceState<SessionRepository?>(null) {
        value = withContext(Dispatchers.Default) { sessionProvider() }
    }
    val themes by produceState<ThemeRepository?>(null) {
        value = withContext(Dispatchers.Default) { themesProvider() }
    }
    val readySession = session
    val readyThemes = themes
    if (readySession == null || readyThemes == null) {
        DarkSplash(holdFinalFrame = true)
        return
    }
    ShellGate(readySession, readyThemes, pending)
}

/** The saved per-device settings that shape the whole shell. Known only once every flow has emitted. */
private data class ShellPrefs(
    val mode: ThemeMode,
    val dynamicColor: Boolean,
    val pureBlack: Boolean,
    val blockScreenshots: Boolean,
    val appLock: Boolean,
)

private fun ThemeRepository.shellPrefs(): Flow<ShellPrefs> =
    combine(mode, dynamicColor, pureBlack, blockScreenshots, appLock) { mode, dynamic, black, secure, lock ->
        ShellPrefs(mode, dynamic, black, secure, lock)
    }

@Composable
private fun ShellGate(session: SessionRepository, themes: ThemeRepository, pending: PendingNavigation) {
    val sessionState by session.state.collectAsState()
    val prefsFlow = remember(themes) { themes.shellPrefs() }
    val prefs by prefsFlow.collectAsState(initial = null)
    val activity = LocalContext.current.findActivity() as? FragmentActivity
    val authenticate: (onSuccess: () -> Unit) -> Unit = remember(activity) {
        { onSuccess -> if (activity != null) showUnlockPrompt(activity, onSuccess) }
    }
    val ready = prefs
    if (ready == null || sessionState == SessionState.Restoring) {
        DarkSplash(holdFinalFrame = true)
    } else {
        val isDark = ready.mode.isDark(isSystemInDarkTheme())
        SystemBarAppearance(isDark = isDark)
        VirgaTheme(darkTheme = isDark, dynamicColor = ready.dynamicColor, pureBlack = ready.pureBlack) {
            val lockEngaged = ready.appLock && rememberDeviceSecure()
            SecureWindow(enabled = ready.blockScreenshots)
            HideFromRecents(enabled = lockEngaged)
            AppLockGate(enabled = lockEngaged, authenticate = authenticate) {
                ShellHost(sessionState, pending)
            }
        }
    }
}

private fun ThemeMode.isDark(systemDark: Boolean): Boolean = when (this) {
    ThemeMode.System -> systemDark
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}
