package com.tomasps.virga.feature.domains.dns

import android.content.ClipData
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.navigation.LocalNavigator
import com.tomasps.virga.core.ui.components.CodeBlock
import com.tomasps.virga.core.ui.components.KeyValueRow
import com.tomasps.virga.core.ui.components.OpenInDashboardRow
import com.tomasps.virga.core.ui.components.SectionCard
import com.tomasps.virga.core.ui.components.SectionHeader
import com.tomasps.virga.core.ui.dialogs.ConfirmDialog
import com.tomasps.virga.core.ui.state.UiStateContent
import com.tomasps.virga.core.ui.state.asString
import com.tomasps.virga.feature.domains.DASHBOARD_URL
import com.tomasps.virga.feature.domains.R
import com.tomasps.virga.feature.domains.zone.CARD_GUTTER
import com.tomasps.virga.feature.domains.zones.humanizeId
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/**
 * The DNS settings tab. Shows DNSSEC with its DS record and the enable and disable actions. Both changes ask first:
 * disabling DNSSEC is needed before a registrar transfer, and enabling it needs the DS record added at the registrar.
 */
@Composable
fun DnsSettingsTab(viewModel: DnssecViewModel, dnsSettings: ZoneDnsSettingsViewModel) {
    val state by viewModel.state.collectAsState()
    val zoneSettings by dnsSettings.state.collectAsState()
    val navigator = LocalNavigator.current
    val enable = state.pendingEnable
    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            SectionHeader(title = stringResource(R.string.domains_section_dnssec), modifier = Modifier.padding(vertical = 8.dp))
        }
        item {
            UiStateContent(state = state.details, onRetry = viewModel::retry) { details ->
                DnssecContent(details, busy = state.busy, onRequest = viewModel::requestChange)
            }
        }
        item {
            state.message?.let { text ->
                Text(text.asString(), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }
        item {
            SectionHeader(title = stringResource(R.string.domains_section_dns_settings), modifier = Modifier.padding(vertical = 8.dp))
        }
        item {
            UiStateContent(state = zoneSettings, onRetry = dnsSettings::retry) { settings ->
                ZoneDnsSettingsContent(settings, onOpenDashboard = { navigator.openInBrowser(DASHBOARD_URL) })
            }
        }
    }
    if (enable != null) {
        ConfirmDialog(
            title = stringResource(if (enable) R.string.domains_confirm_dnssec_enable_title else R.string.domains_confirm_dnssec_disable_title),
            message = stringResource(if (enable) R.string.domains_confirm_dnssec_enable_message else R.string.domains_confirm_dnssec_disable_message),
            onConfirm = viewModel::confirmChange,
            onDismiss = viewModel::dismissChange,
        )
    }
}

@Composable
private fun DnssecContent(details: DnssecDetails, busy: Boolean, onRequest: (Boolean) -> Unit) {
    val enabled = details.state == DnssecState.ACTIVE || details.state == DnssecState.PENDING
    val rows = dnssecRows(details, enabled)
    Column(Modifier.fillMaxWidth()) {
        SectionCard(rows = rows, modifier = Modifier.padding(horizontal = CARD_GUTTER))
        OutlinedButton(
            onClick = { onRequest(!enabled) },
            enabled = !busy && details.state != DnssecState.UNKNOWN,
            modifier = Modifier.padding(horizontal = CARD_GUTTER, vertical = 12.dp),
        ) {
            Text(stringResource(if (enabled) R.string.domains_action_dnssec_disable else R.string.domains_action_dnssec_enable))
        }
    }
}

/** The DNSSEC facts as card rows. The DS record is listed only while DNSSEC is on, because only then is it valid. */
@Composable
private fun dnssecRows(details: DnssecDetails, enabled: Boolean): List<@Composable () -> Unit> = buildList {
    add { KeyValueRow(stringResource(R.string.domains_kv_dnssec_status), dnssecStatusText(details.state)) }
    if (enabled) {
        details.ds?.let { ds -> add { DsRecordBlock(ds) } }
        details.keyTag?.let { tag -> add { KeyValueRow(stringResource(R.string.domains_kv_key_tag), tag.toString()) } }
        details.algorithm?.let { algorithm -> add { KeyValueRow(stringResource(R.string.domains_kv_algorithm), algorithm) } }
    }
}

/** The DS record for the registrar: wrapped so the whole value is visible, with a copy action. */
@Composable
private fun DsRecordBlock(ds: String) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth().padding(horizontal = GUTTER, vertical = 12.dp)) {
        Text(stringResource(R.string.domains_ds_record_label), style = MaterialTheme.typography.bodyMedium)
        CodeBlock(code = ds, wrap = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        OutlinedButton(
            onClick = { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(DS_CLIP_LABEL, ds))) } },
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text(stringResource(R.string.domains_ds_copy))
        }
    }
}

/**
 * Zone DNS settings, read only. Changing the zone mode or the nameservers is left to the dashboard: `dns_only` and
 * `cdn_only` turn off proxying for every record in the zone, and nameserver changes also happen at the registrar.
 */
@Composable
private fun ZoneDnsSettingsContent(settings: ZoneDnsSettings, onOpenDashboard: () -> Unit) {
    val rows: List<@Composable () -> Unit> = buildList {
        add { KeyValueRow(stringResource(R.string.domains_dns_mode), stringResource(zoneModeTextRes(settings.zoneMode))) }
        add { KeyValueRow(stringResource(R.string.domains_dns_nameservers), nameserverTypeText(settings.nameserverType)) }
        add { KeyValueRow(stringResource(R.string.domains_dns_flatten), yesNoText(settings.flattenAllCnames)) }
        add { KeyValueRow(stringResource(R.string.domains_dns_multi_provider), yesNoText(settings.multiProvider)) }
        settings.nsTtl?.let { ttl -> add { KeyValueRow(stringResource(R.string.domains_dns_ns_ttl), ttl.toString()) } }
        add { KeyValueRow(stringResource(R.string.domains_dns_secondary_overrides), yesNoText(settings.secondaryOverrides)) }
        add {
            OpenInDashboardRow(
                label = stringResource(R.string.domains_dns_change_in_dashboard),
                description = stringResource(R.string.domains_dns_change_in_dashboard_desc),
                onOpen = onOpenDashboard,
            )
        }
    }
    SectionCard(rows = rows, modifier = Modifier.fillMaxWidth().padding(horizontal = CARD_GUTTER))
}

/** Words for the nameserver type the API reports, such as `cloudflare.standard`. Unknown types read as plain words. */
@Composable
private fun nameserverTypeText(raw: String?): String = when (raw) {
    NS_STANDARD -> stringResource(R.string.domains_nameservers_standard)
    NS_STANDARD_RANDOM -> stringResource(R.string.domains_nameservers_standard_random)
    null, "" -> stringResource(R.string.domains_status_unknown)
    else -> if (raw.startsWith(NS_CUSTOM_PREFIX)) stringResource(R.string.domains_nameservers_custom) else humanizeId(raw)
}

private fun zoneModeTextRes(mode: ZoneMode): Int = when (mode) {
    ZoneMode.STANDARD -> R.string.domains_dns_mode_standard
    ZoneMode.CDN_ONLY -> R.string.domains_dns_mode_cdn_only
    ZoneMode.DNS_ONLY -> R.string.domains_dns_mode_dns_only
    ZoneMode.UNKNOWN -> R.string.domains_status_unknown
}

@Composable
private fun yesNoText(value: Boolean?): String = stringResource(
    when (value) {
        true -> R.string.domains_value_yes
        false -> R.string.domains_value_no
        null -> R.string.domains_status_unknown
    },
)

@Composable
private fun dnssecStatusText(state: DnssecState): String = stringResource(
    when (state) {
        DnssecState.ACTIVE -> R.string.domains_dnssec_active
        DnssecState.PENDING -> R.string.domains_dnssec_pending
        DnssecState.DISABLED -> R.string.domains_dnssec_disabled
        DnssecState.PENDING_DISABLED -> R.string.domains_dnssec_pending_disabled
        DnssecState.ERROR -> R.string.domains_dnssec_error
        DnssecState.UNKNOWN -> R.string.domains_status_unknown
    },
)

private val GUTTER = 16.dp
private const val NS_STANDARD = "cloudflare.standard"
private const val NS_STANDARD_RANDOM = "cloudflare.standard.random"
private const val NS_CUSTOM_PREFIX = "custom."

private const val DS_CLIP_LABEL = "DS record"
