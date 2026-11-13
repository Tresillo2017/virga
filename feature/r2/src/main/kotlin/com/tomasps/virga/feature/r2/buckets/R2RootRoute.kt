package com.tomasps.virga.feature.r2.buckets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.navigation.LocalNavigator
import com.tomasps.virga.core.navigation.R2Bucket
import com.tomasps.virga.core.ui.components.LoadMoreFooter
import com.tomasps.virga.core.ui.components.ListRow
import com.tomasps.virga.core.ui.components.LoadMoreState
import com.tomasps.virga.core.ui.components.SearchField
import com.tomasps.virga.core.ui.scaffold.ScreenScaffold
import com.tomasps.virga.core.ui.state.EmptyState
import com.tomasps.virga.core.ui.state.UiStateContent
import com.tomasps.virga.core.ui.state.asString
import com.tomasps.virga.feature.r2.R
import com.tomasps.virga.feature.r2.bucket.r2LocationLabel
import com.tomasps.virga.feature.r2.api.R2BucketInfo
import com.tomasps.virga.feature.r2.common.r2DateLabel
import org.koin.compose.viewmodel.koinViewModel

/** Storage hub entry: the account's R2 buckets, with search, paging and creation. */
@Composable
internal fun R2RootRoute(viewModel: R2RootViewModel = koinViewModel()) {
    val navigator = LocalNavigator.current
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.onScreenShown() }
    val message = state.message?.asString()
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            viewModel.messageShown()
        }
    }
    ScreenScaffold(
        title = stringResource(R.string.r2_title_buckets),
        onBack = { navigator.back() },
        actions = {
            IconButton(onClick = viewModel::openCreate) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.r2_action_create_bucket))
            }
        },
        snackbarHostState = snackbar,
        isRefreshing = state.isRefreshing,
        onRefresh = viewModel::refresh,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SearchField(
                query = state.search,
                onQueryChange = viewModel::onSearchChanged,
                placeholder = stringResource(R.string.r2_search_buckets),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            state.buckets?.let { buckets ->
                UiStateContent(state = buckets, onRetry = viewModel::retry) { list ->
                    if (list.isEmpty() && state.search.isNotBlank()) {
                        EmptyState(
                            title = stringResource(R.string.r2_empty_search_title),
                            message = stringResource(R.string.r2_empty_search_buckets_message),
                        )
                    } else if (list.isEmpty()) {
                        EmptyState(
                            title = stringResource(R.string.r2_empty_buckets_title),
                            message = stringResource(R.string.r2_empty_buckets_message),
                        )
                    } else {
                        BucketList(
                            buckets = list,
                            loadMore = state.loadMore,
                            onOpen = { navigator.navigate(R2Bucket(it.name)) },
                            onLoadMore = viewModel::loadMore,
                        )
                    }
                }
            }
        }
    }
    state.create?.let { form ->
        CreateBucketDialog(
            form = form,
            onNameChanged = viewModel::onCreateNameChanged,
            onLocationChanged = viewModel::onCreateLocationChanged,
            onStorageClassChanged = viewModel::onCreateStorageClassChanged,
            onSubmit = viewModel::submitCreate,
            onDismiss = viewModel::dismissCreate,
            onOpenDashboard = { navigator.openInBrowser(R2_DASHBOARD_URL) },
        )
    }
}

@Composable
private fun BucketList(
    buckets: List<R2BucketInfo>,
    loadMore: LoadMoreState,
    onOpen: (R2BucketInfo) -> Unit,
    onLoadMore: () -> Unit,
) {
    val locationUnknown = stringResource(R.string.r2_location_unknown)
    val createdUnknown = stringResource(R.string.r2_created_unknown)
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(buckets, key = { it.name }) { bucket ->
            ListRow(
                headline = bucket.name,
                supporting = stringResource(
                    R.string.r2_bucket_supporting,
                    bucket.location?.takeIf { it.isNotBlank() }?.let { r2LocationLabel(it) } ?: locationUnknown,
                    r2DateLabel(bucket.creationDate, createdUnknown),
                ),
                leading = { Icon(Icons.Filled.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                onClick = { onOpen(bucket) },
            )
        }
        item { LoadMoreFooter(state = loadMore, onLoadMore = onLoadMore) }
    }
}


/** Opened for jurisdiction buckets and other settings the API cannot change from the app. */
internal const val R2_DASHBOARD_URL = "https://dash.cloudflare.com/?to=/:account/r2/overview"
