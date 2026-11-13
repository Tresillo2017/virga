package com.tomasps.virga.shell

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.tomasps.virga.core.auth.session.SessionRepository
import com.tomasps.virga.core.auth.session.SessionState
import com.tomasps.virga.core.navigation.PendingNavigation
import com.tomasps.virga.core.ui.brand.AnimatedSplash
import com.tomasps.virga.core.ui.theme.VirgaTheme
import com.tomasps.virga.feature.account.settings.ThemeMode
import com.tomasps.virga.feature.account.settings.ThemeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
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
    var animationDone by rememberSaveable { mutableStateOf(false) }
    if (!animationDone) {
        val visible by splashVisible.collectAsState()
        DarkSplash(
            holdFinalFrame = false,
            startWhen = visible,
            onStarted = onSplashStarted,
            onFinished = { animationDone = true },
        )
    } else {
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

@Composable
private fun ShellGate(session: SessionRepository, themes: ThemeRepository, pending: PendingNavigation) {
    val sessionState by session.state.collectAsState()
    val themeMode by themes.mode.collectAsState(initial = null)
    val resolvedMode = themeMode
    if (resolvedMode == null || sessionState == SessionState.Restoring) {
        DarkSplash(holdFinalFrame = true)
    } else {
        VirgaTheme(darkTheme = resolvedMode.isDark(isSystemInDarkTheme())) {
            ShellHost(sessionState, pending)
        }
    }
}

@Composable
private fun DarkSplash(
    holdFinalFrame: Boolean,
    startWhen: Boolean = true,
    onStarted: () -> Unit = {},
    onFinished: () -> Unit = {},
) {
    VirgaTheme(darkTheme = true) {
        AnimatedSplash(
            modifier = Modifier.fillMaxSize(),
            holdFinalFrame = holdFinalFrame,
            startWhen = startWhen,
            onStarted = onStarted,
            onFinished = onFinished,
        )
    }
}

private fun ThemeMode.isDark(systemDark: Boolean): Boolean = when (this) {
    ThemeMode.System -> systemDark
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}
