package com.tomasps.virga.feature.account.account

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.navigation.LocalNavigator
import com.tomasps.virga.core.navigation.Settings
import com.tomasps.virga.core.ui.components.KeyValueRow
import com.tomasps.virga.core.ui.components.ListRow
import com.tomasps.virga.core.ui.components.SectionCard
import com.tomasps.virga.core.ui.scaffold.ScreenScaffold
import com.tomasps.virga.core.ui.state.ErrorState
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.feature.account.AccountAbout
import com.tomasps.virga.feature.account.AccountSwitcher
import com.tomasps.virga.feature.account.R
import com.tomasps.virga.feature.account.details.AccountDetails
import org.koin.compose.viewmodel.koinViewModel

private val PAGE_GUTTER = 16.dp
private val LOADING_PADDING = 16.dp

@Composable
fun AccountRoute(viewModel: AccountHomeViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val navigator = LocalNavigator.current
    ScreenScaffold(title = stringResource(R.string.account_tab_title)) { padding ->
        Column(modifier = Modifier.fillMaxWidth().padding(padding).padding(PAGE_GUTTER)) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(PAGE_GUTTER)) {
                    Icon(Icons.Filled.AccountCircle, contentDescription = null)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = state.accountName ?: stringResource(R.string.account_no_account),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    val supporting = when {
                        state.isDemo -> stringResource(R.string.account_demo_badge)
                        else -> pluralStringResource(R.plurals.account_count, state.accountCount, state.accountCount)
                    }
                    Text(supporting, style = MaterialTheme.typography.bodyMedium)
                }
            }
            state.details?.let { details ->
                Spacer(Modifier.height(PAGE_GUTTER))
                AccountDetailsSection(details = details, onRetry = viewModel::retry)
            }
            Spacer(Modifier.height(PAGE_GUTTER))
            SectionCard(
                rows = listOf(
                    {
                        ListRow(
                            headline = stringResource(R.string.account_switch),
                            supporting = pluralStringResource(R.plurals.account_count, state.accountCount, state.accountCount),
                            leading = { Icon(Icons.Filled.SwapHoriz, contentDescription = null) },
                            onClick = { navigator.navigate(AccountSwitcher) },
                        )
                    },
                    {
                        ListRow(
                            headline = stringResource(R.string.account_settings),
                            leading = { Icon(Icons.Filled.Settings, contentDescription = null) },
                            onClick = { navigator.navigate(Settings) },
                        )
                    },
                    {
                        ListRow(
                            headline = stringResource(R.string.account_about),
                            leading = { Icon(Icons.Filled.Info, contentDescription = null) },
                            onClick = { navigator.navigate(AccountAbout) },
                        )
                    },
                ),
            )
        }
    }
}

/**
 * The selected account's facts, or a loading or retry state in their place. The shared UiStateContent is not used
 * here: its loading state fills the whole screen, which pushed the account rows below the fold while details loaded.
 */
@Composable
private fun AccountDetailsSection(details: UiState<AccountDetails>, onRetry: () -> Unit) {
    when (details) {
        is UiState.Loading -> DetailsLoading()
        is UiState.Failure -> ErrorState(error = details.error, onRetry = onRetry)
        is UiState.Content -> AccountFacts(details.data)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DetailsLoading() {
    val label = stringResource(R.string.account_loading)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = LOADING_PADDING)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        LoadingIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun AccountFacts(info: AccountDetails) {
    val rows = buildList<@Composable () -> Unit> {
        add { KeyValueRow(stringResource(R.string.account_detail_id), info.id, monospaceValue = true) }
        friendlyAccountType(info.type)?.let { type ->
            add { KeyValueRow(stringResource(R.string.account_detail_type), type) }
        }
        friendlyCreatedOn(info.createdOn)?.let { created ->
            add { KeyValueRow(stringResource(R.string.account_detail_created), created) }
        }
    }
    SectionCard(rows = rows)
}
