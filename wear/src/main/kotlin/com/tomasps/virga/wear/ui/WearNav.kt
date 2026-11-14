package com.tomasps.virga.wear.ui

import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import com.tomasps.virga.core.common.builds.BuildSummary
import androidx.navigation.navArgument
import androidx.wear.compose.material3.AppScaffold
import com.tomasps.virga.wear.builds.findByKey
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private const val ROUTE_LIST = "builds"
private const val ARG_SCRIPT = "script"
private const val ARG_UUID = "buildUuid"
private const val ROUTE_DETAIL = "build"

/** The detail route for [build], or null when a part is blank: an empty segment would not match any route. */
internal fun detailRoute(build: BuildSummary): String? = detailRoute(build.scriptName, build.buildUuid)

internal fun detailRoute(scriptName: String, buildUuid: String): String? {
    if (scriptName.isBlank() || buildUuid.isBlank()) return null
    return "$ROUTE_DETAIL/${Uri.encode(scriptName)}/${Uri.encode(buildUuid)}"
}

/** The watch app: the build list, and a detail screen for the build that was tapped. */
@Composable
fun WearApp(startRoute: String? = null) {
    VirgaWearTheme {
        AppScaffold {
            val nav = rememberSwipeDismissableNavController()
            val listViewModel = koinViewModel<BuildListViewModel>()
            val listState by listViewModel.state.collectAsState()
            LeaveDetailWhenSessionEnds(nav, listState)
            OpenStartRoute(nav, startRoute, listState)
            SwipeDismissableNavHost(navController = nav, startDestination = ROUTE_LIST) {
                composable(ROUTE_LIST) {
                    BuildListScreen(
                        state = listState,
                        onOpen = { build -> detailRoute(build)?.let(nav::navigate) },
                        onRefresh = listViewModel::refresh,
                    )
                }
                composable(
                    route = "$ROUTE_DETAIL/{$ARG_SCRIPT}/{$ARG_UUID}",
                    arguments = listOf(
                        navArgument(ARG_SCRIPT) { type = NavType.StringType },
                        navArgument(ARG_UUID) { type = NavType.StringType },
                    ),
                ) { entry ->
                    val script = entry.arguments?.getString(ARG_SCRIPT).orEmpty()
                    val uuid = entry.arguments?.getString(ARG_UUID).orEmpty()
                    // Found by key, once: the list may reload or reorder behind this screen without changing it.
                    val build = remember(script, uuid) {
                        (listViewModel.state.value as? ListState.Content)?.builds?.findByKey(script, uuid)
                    }
                    if (build == null) {
                        LaunchedEffect(Unit) { nav.popBackStack() }
                        return@composable
                    }
                    val detailViewModel = koinViewModel<BuildDetailViewModel>(
                        key = "$script/$uuid",
                        parameters = { parametersOf(build) },
                    )
                    val detail by detailViewModel.state.collectAsState()
                    val isOrphaned by detailViewModel.isOrphaned.collectAsState()
                    LaunchedEffect(isOrphaned) { if (isOrphaned) nav.popBackStack() }
                    LaunchedEffect(detail.action) {
                        if (detail.action == ActionState.Done) listViewModel.refresh()
                    }
                    BuildDetailScreen(
                        state = detail,
                        onCancel = detailViewModel::cancel,
                        onRetry = detailViewModel::retry,
                        onConfirm = detailViewModel::confirm,
                        onDismiss = detailViewModel::dismiss,
                        onWatch = detailViewModel::watch,
                        onStopWatching = detailViewModel::stopWatching,
                    )
                }
            }
        }
    }
}

/**
 * Opens the build a tile tap asked for, once, after the list has loaded: the detail screen finds its build in the
 * list. If the list cannot supply content (not connected, empty, error) the request is dropped and the list shows.
 */
@Composable
private fun OpenStartRoute(nav: NavHostController, startRoute: String?, listState: ListState) {
    var isHandled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(listState) {
        if (startRoute == null || isHandled || listState == ListState.Loading) return@LaunchedEffect
        isHandled = true
        if (listState !is ListState.Content) return@LaunchedEffect
        // A route the graph rejects must not crash the app: stay on the list.
        try {
            nav.navigate(startRoute)
        } catch (@Suppress("TooGenericExceptionCaught") error: RuntimeException) {
            Log.w("VirgaWear", "Start route rejected: ${error::class.java.name}")
        }
    }
}

/** A wiped or stale session leaves nothing to show on a detail screen, so return to the list's connect message. */
@Composable
private fun LeaveDetailWhenSessionEnds(nav: NavHostController, listState: ListState) {
    LaunchedEffect(listState) {
        if (listState == ListState.NotConnected || listState == ListState.Stale) {
            nav.popBackStack(ROUTE_LIST, inclusive = false)
        }
    }
}
