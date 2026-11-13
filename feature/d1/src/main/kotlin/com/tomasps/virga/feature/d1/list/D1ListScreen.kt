package com.tomasps.virga.feature.d1.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tomasps.virga.core.common.formatBytes
import com.tomasps.virga.core.navigation.D1Database as D1DatabaseKey
import com.tomasps.virga.core.navigation.LocalNavigator
import com.tomasps.virga.core.ui.components.ListRow
import com.tomasps.virga.core.ui.components.SectionHeader
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import com.tomasps.virga.core.ui.components.LoadMoreFooter
import com.tomasps.virga.core.ui.dialogs.TypedNameConfirmDialog
import com.tomasps.virga.core.ui.scaffold.ScreenScaffold
import com.tomasps.virga.core.ui.state.ErrorUi
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.core.ui.state.UiStateContent
import com.tomasps.virga.core.ui.state.EmptyState
import com.tomasps.virga.core.ui.state.asString
import com.tomasps.virga.core.ui.components.DestructiveMenuItem
import com.tomasps.virga.feature.d1.R
import com.tomasps.virga.feature.d1.data.D1Database
import com.tomasps.virga.feature.d1.ui.formatCreatedOn
import com.tomasps.virga.feature.d1.list.D1_JURISDICTION_LABEL_RES
import com.tomasps.virga.feature.d1.list.D1_LOCATION_LABEL_RES
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun D1ListRoute() {
    val viewModel: D1ListViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    val navigator = LocalNavigator.current
    ReloadOnResume(onResume = viewModel::onResumed)
    D1ListScreen(
        state = state,
        onBack = { navigator.back() },
        onRefresh = viewModel::refresh,
        onRetry = viewModel::refresh,
        onOpen = { database -> database.uuid?.let { navigator.navigate(D1DatabaseKey(it)) } },
        onLoadMore = viewModel::loadMore,
        onCreate = viewModel::openCreate,
        onDismissCreate = viewModel::dismissCreate,
        onNameChanged = viewModel::onNameChanged,
        onJurisdictionChanged = viewModel::onJurisdictionChanged,
        onLocationChanged = viewModel::onLocationChanged,
        onSubmitCreate = viewModel::submitCreate,
        onRequestDelete = viewModel::requestDelete,
        onCancelDelete = viewModel::cancelDelete,
        onConfirmDelete = viewModel::confirmDelete,
        onDismissActionError = viewModel::dismissActionError,
        onRestore = viewModel::restore,
    )
}

/** Tells the view model each time this screen is resumed, so it can reload after a database screen changed the list. */
@Composable
private fun ReloadOnResume(onResume: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

@Composable
internal fun D1ListScreen(
    state: D1ListUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (D1Database) -> Unit,
    onLoadMore: () -> Unit,
    onCreate: () -> Unit,
    onDismissCreate: () -> Unit,
    onNameChanged: (String) -> Unit,
    onJurisdictionChanged: (String?) -> Unit,
    onLocationChanged: (String?) -> Unit,
    onSubmitCreate: () -> Unit,
    onRequestDelete: (D1Database) -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissActionError: () -> Unit,
    onRestore: (RecentlyDeletedDatabase) -> Unit,
) {
    ScreenScaffold(
        title = stringResource(R.string.d1_title_d1root),
        onBack = onBack,
        onRefresh = onRefresh,
        isRefreshing = state.isRefreshing,
        actions = {
            IconButton(onClick = onCreate) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.d1_create_database))
            }
        },
    ) { padding ->
        UiStateContent(state = state.databases, modifier = Modifier.padding(padding), onRetry = onRetry) { content ->
            Column(Modifier.fillMaxWidth()) {
                DatabaseListContentView(
                    content = content,
                    onOpen = onOpen,
                    onLoadMore = onLoadMore,
                    onRequestDelete = onRequestDelete,
                )
                RecentlyDeletedSection(
                    entries = state.recentlyDeleted,
                    restoringUuid = state.restoringUuid,
                    onRestore = onRestore,
                )
            }
        }
    }
    state.create?.let { draft ->
        CreateDatabaseDialog(
            name = draft.name,
            nameError = draft.nameError,
            jurisdiction = draft.jurisdiction,
            locationHint = draft.locationHint,
            isSubmitting = draft.isSubmitting,
            error = draft.error,
            onNameChanged = onNameChanged,
            onJurisdictionChanged = onJurisdictionChanged,
            onLocationChanged = onLocationChanged,
            onSubmit = onSubmitCreate,
            onDismiss = onDismissCreate,
        )
    }
    state.deleteTarget?.let { target ->
        TypedNameConfirmDialog(
            title = stringResource(R.string.d1_delete_title),
            message = stringResource(R.string.d1_delete_message, target.name.orEmpty()),
            expectedName = target.name.orEmpty(),
            onConfirm = onConfirmDelete,
            onDismiss = onCancelDelete,
        )
    }
    state.actionError?.let { error -> ActionErrorDialog(error = error, onDismiss = onDismissActionError) }
}

@Composable
private fun DatabaseListContentView(
    content: DatabaseListContent,
    onOpen: (D1Database) -> Unit,
    onLoadMore: () -> Unit,
    onRequestDelete: (D1Database) -> Unit,
) {
    if (content.items.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.d1_list_empty_title),
            message = stringResource(R.string.d1_list_empty_message),
        )
        return
    }
    Column(Modifier.fillMaxWidth()) {
        content.items.forEach { database ->
            DatabaseRow(database = database, onOpen = { onOpen(database) }, onRequestDelete = { onRequestDelete(database) })
        }
        LoadMoreFooter(state = content.loadMore, onLoadMore = onLoadMore)
    }
}

/** The restore deadline as a readable local date, such as "Dec 1, 2026". Never the ISO form. */
private fun restoreDate(entry: RecentlyDeletedDatabase): String =
    Instant.ofEpochMilli(RecentlyDeletedRules.restoreDeadlineMillis(entry))
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

/** Databases this device deleted that the API can still restore. Hidden when there are none. */
@Composable
private fun RecentlyDeletedSection(
    entries: List<RecentlyDeletedDatabase>,
    restoringUuid: String?,
    onRestore: (RecentlyDeletedDatabase) -> Unit,
) {
    if (entries.isEmpty()) return
    SectionHeader(
        title = stringResource(R.string.d1_recently_deleted_title),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    Text(
        stringResource(R.string.d1_recently_deleted_message),
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    entries.forEach { entry ->
        ListRow(
            headline = entry.name,
            supporting = stringResource(R.string.d1_recently_deleted_row, restoreDate(entry)),
            trailing = {
                TextButton(
                    onClick = { onRestore(entry) },
                    enabled = restoringUuid == null,
                ) {
                    Text(stringResource(R.string.d1_restore_action))
                }
            },
        )
    }
}

/** The readable name of a jurisdiction the API returned. Known values come from their string resources. */
@Composable
private fun jurisdictionLabel(wire: String): String = D1_JURISDICTION_LABEL_RES[wire]?.let { stringResource(it) } ?: wire

@Composable
private fun DatabaseRow(database: D1Database, onOpen: () -> Unit, onRequestDelete: () -> Unit) {
    val size = database.fileSize?.let { formatBytes(it) }
    val jurisdiction = database.jurisdiction?.let { wire -> jurisdictionLabel(wire) }
    val supporting = listOfNotNull(size, formatCreatedOn(database.createdAt), jurisdiction?.let { "${stringResource(R.string.d1_jurisdiction_label)}: $it" })
        .joinToString(" · ")
        .ifEmpty { null }
    var menuOpen by remember { mutableStateOf(false) }
    ListRow(
        headline = database.name.orEmpty(),
        supporting = supporting,
        onClick = onOpen,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.d1_database_actions, database.name.orEmpty()))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DestructiveMenuItem(
                        text = stringResource(R.string.d1_delete_action),
                        onClick = {
                            menuOpen = false
                            onRequestDelete()
                        },
                    )
                }
            }
        },
    )
}

@Composable
private fun CreateDatabaseDialog(
    name: String,
    nameError: Boolean,
    jurisdiction: String?,
    locationHint: String?,
    isSubmitting: Boolean,
    error: ErrorUi?,
    onNameChanged: (String) -> Unit,
    onJurisdictionChanged: (String?) -> Unit,
    onLocationChanged: (String?) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text(stringResource(R.string.d1_create_title)) },
        text = {
            CreateDatabaseFields(
                name = name,
                nameError = nameError,
                jurisdiction = jurisdiction,
                locationHint = locationHint,
                error = error,
                onNameChanged = onNameChanged,
                onJurisdictionChanged = onJurisdictionChanged,
                onLocationChanged = onLocationChanged,
            )
        },
        confirmButton = {
            Button(onClick = onSubmit, enabled = !isSubmitting) { Text(stringResource(R.string.d1_create_action)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) { Text(stringResource(R.string.d1_cancel)) }
        },
    )
}

/** The create form without its dialog: the name, the two choices, and the error from the last attempt. */
@Composable
internal fun CreateDatabaseFields(
    name: String,
    nameError: Boolean,
    jurisdiction: String?,
    locationHint: String?,
    error: ErrorUi?,
    onNameChanged: (String) -> Unit,
    onJurisdictionChanged: (String?) -> Unit,
    onLocationChanged: (String?) -> Unit,
) {
    val jurisdictionLabels = D1_JURISDICTIONS.associateWith { stringResource(D1_JURISDICTION_LABEL_RES.getValue(it)) }
    val locationLabels = D1_LOCATION_HINTS.associateWith { stringResource(D1_LOCATION_LABEL_RES.getValue(it)) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChanged,
            label = { Text(stringResource(R.string.d1_create_name_label)) },
            isError = nameError,
            supportingText = { if (nameError) Text(stringResource(R.string.d1_create_name_required)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.d1_jurisdiction_label), style = MaterialTheme.typography.labelLarge)
        ChipRow(
            options = D1_JURISDICTIONS,
            labels = jurisdictionLabels,
            selected = jurisdiction,
            defaultLabel = stringResource(R.string.d1_default_option),
            onSelected = onJurisdictionChanged,
        )
        Text(stringResource(R.string.d1_location_label), style = MaterialTheme.typography.labelLarge)
        ChipRow(
            options = D1_LOCATION_HINTS,
            labels = locationLabels,
            selected = locationHint,
            defaultLabel = stringResource(R.string.d1_default_option),
            onSelected = onLocationChanged,
        )
        Text(stringResource(R.string.d1_jurisdiction_permanent_note), style = MaterialTheme.typography.bodySmall)
        error?.let { ErrorLine(it) }
    }
}

@Composable
private fun ErrorLine(error: ErrorUi) {
    Column {
        Text(error.title.asString(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
        Text(error.message.asString(), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ChipRow(
    options: List<String>,
    labels: Map<String, String>,
    selected: String?,
    defaultLabel: String,
    onSelected: (String?) -> Unit,
) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = selected == null, onClick = { onSelected(null) }, label = { Text(defaultLabel) })
        options.forEach { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelected(option) },
                label = { Text(labels[option] ?: option) },
            )
        }
    }
}

@Composable
private fun ActionErrorDialog(error: ErrorUi, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(error.title.asString()) },
        text = { Text(error.message.asString()) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.d1_ok)) } },
    )
}
