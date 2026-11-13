package com.tomasps.virga.feature.account.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.navigation.LocalNavigator
import com.tomasps.virga.core.ui.brand.VirgaLockup
import com.tomasps.virga.core.ui.scaffold.ScreenScaffold
import com.tomasps.virga.feature.account.R

private const val UNKNOWN_VERSION = "?"

@Composable
fun AboutRoute() {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val version = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: UNKNOWN_VERSION
    }
    ScreenScaffold(
        title = stringResource(R.string.about_title),
        onBack = { navigator.back() },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
        ) {
            VirgaLockup(markSize = 40.dp)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.about_version, version), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.account_sign_in_tagline), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.about_not_affiliated), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.about_trademark), style = MaterialTheme.typography.bodySmall)
        }
    }
}
