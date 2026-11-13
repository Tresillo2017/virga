package com.tomasps.virga.feature.r2.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.ui.components.OpenInDashboardRow
import com.tomasps.virga.core.ui.components.SectionHeader
import com.tomasps.virga.feature.r2.R
import com.tomasps.virga.core.navigation.LocalNavigator
import com.tomasps.virga.core.ui.state.asString
import com.tomasps.virga.feature.r2.buckets.R2_DASHBOARD_URL
import com.tomasps.virga.feature.r2.common.MessageStrip
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Bucket settings: public access, custom domains, lock rules, CORS and lifecycle. Each section loads and saves on
 * its own. Event notifications are shown as an "Open in dashboard" row, because their API is not verified yet.
 */
@Composable
internal fun R2SettingsTab(bucketName: String, refreshKey: Int) {
    val settings: R2SettingsViewModel = koinViewModel(parameters = { parametersOf(bucketName) })
    val cors: R2CorsViewModel = koinViewModel(parameters = { parametersOf(bucketName) })
    val lifecycle: R2LifecycleViewModel = koinViewModel(parameters = { parametersOf(bucketName) })
    val settingsState by settings.state.collectAsState()
    val corsState by cors.state.collectAsState()
    val lifecycleState by lifecycle.state.collectAsState()
    val navigator = LocalNavigator.current
    val message = settingsState.message ?: corsState.message ?: lifecycleState.message
    LaunchedEffect(refreshKey) {
        if (refreshKey > 0) {
            settings.retryAll()
            cors.retry()
            lifecycle.retry()
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
    ) {
        message?.let { text ->
            MessageStrip(
                text = text.asString(),
                onDismiss = {
                    settings.messageShown()
                    cors.messageShown()
                    lifecycle.messageShown()
                },
            )
        }
        PublicAccessSection(settingsState, settings)
        DomainsSection(settingsState, settings)
        LockSection(settingsState, settings)
        CorsSection(corsState, cors)
        LifecycleSection(lifecycleState, lifecycle)
        SectionHeader(title = stringResource(R.string.r2_section_events))
        Text(
            text = stringResource(R.string.r2_events_description),
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        OpenInDashboardRow(
            onOpen = { navigator.openInBrowser(R2_DASHBOARD_URL) },
            description = stringResource(R.string.r2_events_dashboard_note),
        )
    }
}
