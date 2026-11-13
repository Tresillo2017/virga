package com.tomasps.virga.feature.kv.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.tomasps.virga.core.navigation.KvNamespace
import com.tomasps.virga.core.navigation.LocalNavigator
import com.tomasps.virga.core.ui.components.ListRow
import com.tomasps.virga.core.ui.scaffold.ScreenScaffold
import com.tomasps.virga.core.ui.state.EmptyState
import com.tomasps.virga.core.ui.state.ErrorState
import com.tomasps.virga.core.ui.state.LoadingState
import com.tomasps.virga.feature.kv.R
import com.tomasps.virga.feature.kv.data.KvNamespaceDto
import com.tomasps.virga.feature.kv.namespaces.KvNamespacesViewModel
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.core.ui.state.UiText
import org.koin.compose.viewmodel.koinViewModel

/** The namespaces of the selected account. Each row opens the namespace's keys. */
@Composable
fun KvNamespacesRoute(viewModel: KvNamespacesViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val navigator = LocalNavigator.current
    val snackbar = remember { SnackbarHostState() }
    var showCreate by rememberSaveable { mutableStateOf(false) }
    KvMessageEffect(viewModel.messages, snackbar)
    ScreenScaffold(
        title = stringResource(R.string.kv_title_kvroot),
        onBack = { navigator.back() },
        snackbarHostState = snackbar,
        isRefreshing = state.isRefreshing,
        onRefresh = viewModel::refresh,
        actions = {
            IconButton(onClick = { showCreate = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.kv_action_create_namespace))
            }
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxWidth(), contentPadding = padding) {
            val namespaces = state.namespaces ?: return@LazyColumn
            when (namespaces) {
                is UiState.Loading -> item { LoadingState() }
                is UiState.Failure -> item {
                    ErrorState(error = namespaces.error, onRetry = viewModel::refresh)
                }
                is UiState.Content -> {
                    if (namespaces.data.isEmpty()) {
                        item {
                            EmptyState(
                                title = stringResource(R.string.kv_namespaces_empty_title),
                                message = stringResource(R.string.kv_namespaces_empty_message),
                            )
                        }
                    } else {
                        items(namespaces.data, key = { it.id }) { namespace ->
                            NamespaceRow(namespace) { navigator.navigate(KvNamespace(namespace.id)) }
                        }
                    }
                }
            }
        }
    }
    if (showCreate) {
        KvCreateNamespaceDialog(
            onConfirm = { title, jurisdiction ->
                viewModel.create(title, jurisdiction)
                showCreate = false
            },
            onDismiss = { showCreate = false },
        )
    }
}
@Composable
private fun NamespaceRow(namespace: KvNamespaceDto, onOpen: () -> Unit) {
    val headline = namespace.title.ifBlank { namespace.id }
    val supporting = if (namespace.title.isBlank()) null else namespace.id
    ListRow(
        headline = headline,
        supporting = supporting,
        leading = { Icon(Icons.Filled.Storage, contentDescription = null) },
        onClick = onOpen,
    )
}
