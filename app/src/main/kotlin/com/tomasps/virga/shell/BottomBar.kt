package com.tomasps.virga.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavKey
import com.tomasps.virga.R
import com.tomasps.virga.core.navigation.AccountRoot
import com.tomasps.virga.core.navigation.ComputeRoot
import com.tomasps.virga.core.navigation.DomainsRoot
import com.tomasps.virga.core.navigation.HomeRoot
import com.tomasps.virga.core.navigation.Navigator
import com.tomasps.virga.core.navigation.StorageRoot
import com.tomasps.virga.core.navigation.TopLevelRoute

private data class TabSpec(val key: TopLevelRoute, val label: Int, val icon: ImageVector)

private val TABS = listOf(
    TabSpec(HomeRoot, R.string.tab_home, Icons.Filled.Home),
    TabSpec(ComputeRoot, R.string.tab_compute, Icons.Filled.Memory),
    TabSpec(DomainsRoot, R.string.tab_domains, Icons.Filled.Language),
    TabSpec(StorageRoot, R.string.tab_storage, Icons.Filled.Dns),
    TabSpec(AccountRoot, R.string.tab_account, Icons.Filled.AccountCircle),
)

/**
 * The top-level tabs, as items of a navigation suite: a bottom bar on compact windows and a navigation rail on wider
 * ones. Shown only by [ShellHost] while a top-level tab is the current destination.
 */
internal fun NavigationSuiteScope.virgaTopLevelItems(current: NavKey?, navigator: Navigator) {
    TABS.forEach { tab ->
        item(
            selected = current == tab.key,
            onClick = { if (current != tab.key) navigator.replaceAll(tab.key) },
            icon = { Icon(tab.icon, contentDescription = null) },
            label = { Text(stringResource(tab.label)) },
        )
    }
}
