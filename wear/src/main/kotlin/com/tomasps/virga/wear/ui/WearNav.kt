package com.tomasps.virga.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private const val ROUTE_LIST = "builds"
private const val ARG_INDEX = "index"
private const val ROUTE_DETAIL = "build"

/** The watch app: the build list, and a detail screen for the build that was tapped. */
@Composable
fun WearApp() {
    VirgaWearTheme {
        AppScaffold {
            val nav = rememberSwipeDismissableNavController()
            val listViewModel = koinViewModel<BuildListViewModel>()
            val listState by listViewModel.state.collectAsState()
            LeaveDetailWhenSessionEnds(nav, listState)
            SwipeDismissableNavHost(navController = nav, startDestination = ROUTE_LIST) {
                composable(ROUTE_LIST) {
                    BuildListScreen(
                        state = listState,
                        onOpen = { index -> nav.navigate("$ROUTE_DETAIL/$index") },
                        onRefresh = listViewModel::refresh,
                    )
                }
                composable(
                    route = "$ROUTE_DETAIL/{$ARG_INDEX}",
                    arguments = listOf(navArgument(ARG_INDEX) { type = NavType.IntType }),
                ) { entry ->
                    val index = entry.arguments?.getInt(ARG_INDEX) ?: -1
                    // Looked up once: the list may reload behind this screen (after a retry) without moving it.
                    val build = remember(index) {
                        (listViewModel.state.value as? ListState.Content)?.builds?.getOrNull(index)
                    }
                    if (build == null) {
                        LaunchedEffect(Unit) { nav.popBackStack() }
                        return@composable
                    }
                    val detailViewModel = koinViewModel<BuildDetailViewModel>(
                        key = "$index",
                        parameters = { parametersOf(build) },
                    )
                    val detail by detailViewModel.state.collectAsState()
                    LaunchedEffect(detail.action) {
                        if (detail.action == ActionState.Done) listViewModel.refresh()
                    }
                    BuildDetailScreen(
                        state = detail,
                        onCancel = detailViewModel::cancel,
                        onRetry = detailViewModel::retry,
                        onConfirm = detailViewModel::confirm,
                        onDismiss = detailViewModel::dismiss,
                    )
                }
            }
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
