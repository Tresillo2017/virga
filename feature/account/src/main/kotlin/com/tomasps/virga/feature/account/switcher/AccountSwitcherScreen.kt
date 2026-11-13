package com.tomasps.virga.feature.account.switcher

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.navigation.LocalNavigator
import com.tomasps.virga.core.ui.components.ListRow
import com.tomasps.virga.core.ui.components.SectionCard
import com.tomasps.virga.core.ui.scaffold.ScreenScaffold
import com.tomasps.virga.core.ui.state.EmptyState
import com.tomasps.virga.feature.account.R
import org.koin.compose.viewmodel.koinViewModel

private val PAGE_GUTTER = 16.dp

@Composable
private fun AccountRowItem(row: AccountRow, onSelect: () -> Unit) {
    ListRow(
        headline = row.name,
        supporting = row.id,
        trailing = if (row.isSelected) {
            { Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.account_selected)) }
        } else {
            null
        },
        onClick = onSelect,
    )
}

@Composable
fun AccountSwitcherRoute(viewModel: AccountSwitcherViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val navigator = LocalNavigator.current
    ScreenScaffold(
        title = stringResource(R.string.account_switcher_title),
        onBack = { navigator.back() },
    ) { padding ->
        if (state.rows.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.account_switcher_empty_title),
                message = stringResource(R.string.account_switcher_empty_message),
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(PAGE_GUTTER)
                    .verticalScroll(rememberScrollState()),
            ) {
                SectionCard(
                    rows = state.rows.map { row ->
                        {
                            AccountRowItem(row) { viewModel.onAccountSelected(row.id) { navigator.back() } }
                        }
                    },
                )
            }
        }
    }
}
