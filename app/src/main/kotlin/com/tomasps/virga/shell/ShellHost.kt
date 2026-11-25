package com.tomasps.virga.shell

import androidx.activity.compose.BackHandler
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.motionScheme
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.tomasps.virga.core.auth.session.SessionState
import com.tomasps.virga.core.navigation.BuildDetail
import com.tomasps.virga.core.navigation.HomeRoot
import com.tomasps.virga.core.navigation.LocalNavigator
import com.tomasps.virga.core.navigation.Navigator
import com.tomasps.virga.core.navigation.PendingNavigation
import com.tomasps.virga.core.navigation.RouteRegistry
import com.tomasps.virga.core.navigation.SignIn
import com.tomasps.virga.core.navigation.TopLevelRoute

/**
 * Owns the back stack and renders it. The stack is saved with the instance state, so a key survives
 * process death only if the registry knows how to save it; see RouteRegistry.
 *
 * The top-level tabs sit in a navigation suite: a bottom bar on compact windows, a rail on wider ones. Deeper screens
 * get no suite at all. Screen changes use the theme's motion scheme, forwards and back alike.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShellHost(
    sessionState: SessionState,
    pending: PendingNavigation,
    shell: ShellState = rememberShellState(sessionState),
) {
    val registry = shell.registry
    val backStack = shell.backStack
    val uriHandler = LocalUriHandler.current
    val navigator = remember(backStack) { Navigator(backStack) { uriHandler.openUri(it) } }
    val entryProvider = remember(registry) { registry.buildEntryProvider() }
    val motionScheme = MaterialTheme.motionScheme

    // One effect, so the session's stack fix-up always runs before any pending link is shown.
    LaunchedEffect(sessionState) {
        keepStackMatchingSession(sessionState, navigator)
        if (sessionState is SessionState.SignedIn) {
            val knownAccountIds = sessionState.accounts.map { it.id }.toSet()
            // A browsable link can name any account: only open builds that belong to one signed in here.
            pending.keys.collect { key ->
                if (key !is BuildDetail || key.accountId in knownAccountIds) navigator.navigate(key)
            }
        }
    }

    // Back on a bottom tab's root (other than Home) returns to Home. On Home the handler is off, so the system leaves the app.
    BackHandler(enabled = shouldReturnToHome(sessionState, navigator.current, backStack.size)) {
        navigator.replaceAll(HomeRoot)
    }

    CompositionLocalProvider(LocalNavigator provides navigator) {
        NavigationSuiteScaffold(
            navigationSuiteItems = { virgaTopLevelItems(current = navigator.current, navigator = navigator) },
            layoutType = navigationLayoutFor(navigator.current),
        ) {
            NavDisplay(
                backStack = backStack,
                onBack = { navigator.back() },
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                entryProvider = entryProvider,
                transitionSpec = { forwardTransition(motionScheme) },
                popTransitionSpec = { backTransition(motionScheme) },
                predictivePopTransitionSpec = { _ -> backTransition(motionScheme) },
            )
        }
    }
}

/** The suite is laid out for the window only while a top-level tab is current; deeper screens get none. */
@Composable
private fun navigationLayoutFor(current: NavKey?): NavigationSuiteType =
    if (current is TopLevelRoute) {
        NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())
    } else {
        NavigationSuiteType.None
    }

/** The route registry and the back stack it saves. */
class ShellState(val registry: RouteRegistry, val backStack: NavBackStack<NavKey>)

/**
 * The shell's registry and back stack, saved with the instance state. Create it above anything that can take
 * [ShellHost] out of composition (the app lock), so the stack survives: unlocking returns to the same screen.
 */
@Composable
fun rememberShellState(sessionState: SessionState): ShellState {
    val registry = remember { buildRouteRegistry() }
    val backStack = rememberNavBackStack(registry.savedStateConfiguration(), startKeyFor(sessionState))
    return remember(registry, backStack) { ShellState(registry, backStack) }
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
