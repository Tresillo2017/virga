package com.tomasps.virga.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.tomasps.virga.core.auth.session.SessionState
import com.tomasps.virga.core.navigation.HomeRoot
import com.tomasps.virga.core.navigation.LocalNavigator
import com.tomasps.virga.core.navigation.Navigator
import com.tomasps.virga.core.navigation.PendingNavigation
import com.tomasps.virga.core.navigation.SignIn

/**
 * Owns the back stack and renders it. The stack is saved with the instance state, so a key survives
 * process death only if the registry knows how to save it; see RouteRegistry.
 */
@Composable
fun ShellHost(sessionState: SessionState, pending: PendingNavigation) {
    val registry = remember { buildRouteRegistry() }
    val backStack = rememberNavBackStack(registry.savedStateConfiguration(), startKeyFor(sessionState))
    val uriHandler = LocalUriHandler.current
    val navigator = remember(backStack) { Navigator(backStack) { uriHandler.openUri(it) } }
    val entryProvider = remember(registry) { registry.buildEntryProvider() }

    // One effect, so the session's stack fix-up always runs before any pending link is shown.
    LaunchedEffect(sessionState) {
        keepStackMatchingSession(sessionState, navigator)
        if (sessionState is SessionState.SignedIn) pending.keys.collect { navigator.navigate(it) }
    }

    // Back on a bottom tab's root (other than Home) returns to Home. On Home the handler is off, so the system leaves the app.
    BackHandler(enabled = shouldReturnToHome(sessionState, navigator.current, backStack.size)) {
        navigator.replaceAll(HomeRoot)
    }

    CompositionLocalProvider(LocalNavigator provides navigator) {
        Scaffold(
            bottomBar = { VirgaBottomBar(current = navigator.current, navigator = navigator) },
        ) { padding ->
            NavDisplay(
                backStack = backStack,
                onBack = { navigator.back() },
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                entryProvider = entryProvider,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

/** True when back should land on Home: signed in, on a single-key stack whose root is not Home. */
internal fun shouldReturnToHome(sessionState: SessionState, current: NavKey?, stackSize: Int): Boolean =
    sessionState is SessionState.SignedIn && stackSize == ROOT_ONLY_STACK_SIZE && current != null && current != HomeRoot

private const val ROOT_ONLY_STACK_SIZE = 1

private fun startKeyFor(sessionState: SessionState): NavKey =
    if (sessionState is SessionState.SignedIn) HomeRoot else SignIn

/** Signed out means the sign-in screen is the only destination; signed in means it is never shown. */
private fun keepStackMatchingSession(sessionState: SessionState, navigator: Navigator) {
    val top = navigator.current
    when (sessionState) {
        SessionState.SignedOut -> if (top != SignIn) navigator.replaceAll(SignIn)
        is SessionState.SignedIn -> if (top == null || top == SignIn) navigator.replaceAll(HomeRoot)
        SessionState.Restoring -> Unit
    }
}
