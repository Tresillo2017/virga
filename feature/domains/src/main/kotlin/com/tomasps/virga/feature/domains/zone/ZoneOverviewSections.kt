package com.tomasps.virga.feature.domains.zone

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.common.formatBytes
import com.tomasps.virga.core.common.formatCompactCount
import com.tomasps.virga.core.common.formatDuration
import com.tomasps.virga.core.common.formatIsoDate
import com.tomasps.virga.core.common.formatPercent
import com.tomasps.virga.core.ui.chart.ChartPoint
import com.tomasps.virga.core.ui.chart.LineChart
import com.tomasps.virga.core.ui.components.KeyValueRow
import com.tomasps.virga.core.ui.components.SectionCard
import com.tomasps.virga.core.ui.components.ListRow
import com.tomasps.virga.core.ui.components.SectionHeader
import com.tomasps.virga.core.ui.components.StatTile
import com.tomasps.virga.core.ui.components.TimeRangePicker
import com.tomasps.virga.core.ui.components.TimeRange
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.core.ui.state.UiStateContent
import com.tomasps.virga.feature.domains.R
import com.tomasps.virga.feature.domains.analytics.ZoneTraffic
import com.tomasps.virga.feature.domains.zones.RegistrarRegistration
import com.tomasps.virga.feature.domains.zones.WorkerRoute
import com.tomasps.virga.feature.domains.zones.Zone
import com.tomasps.virga.feature.domains.zones.humanizeId
import com.tomasps.virga.feature.domains.zones.zoneStatusLabel

private const val MILLIS_PER_SECOND = 1_000L

/** Name servers, status and plan of the zone, as one grouped card. */
@Composable
fun ZoneStatusSection(zone: Zone) {
    val none = stringResource(R.string.domains_no_value)
    val rows: List<@Composable () -> Unit> = listOf(
        { KeyValueRow(key = stringResource(R.string.domains_kv_status), value = zoneStatusLabel(zone.status)) },
        { KeyValueRow(key = stringResource(R.string.domains_kv_plan), value = zone.plan?.name.orEmpty().ifEmpty { stringResource(R.string.domains_status_unknown) }) },
        { KeyValueRow(key = stringResource(R.string.domains_kv_nameservers), value = zone.nameServers.joinToString("\n").ifEmpty { none }, monospaceValue = true) },
    )
    SectionCard(rows = rows, modifier = Modifier.padding(horizontal = CARD_GUTTER, vertical = 8.dp))
}

/** Analytics tiles and a requests chart for the chosen window. Each figure reads from the same GraphQL result. */
@Composable
fun ZoneTrafficSection(
    traffic: UiState<ZoneTraffic>,
    range: TimeRange,
    onRange: (TimeRange) -> Unit,
    onRetry: () -> Unit,
) {
    SectionHeader(title = stringResource(R.string.domains_section_traffic))
    TimeRangePicker(selected = range, onSelected = onRange, modifier = Modifier.padding(horizontal = 16.dp))
    UiStateContent(state = traffic, onRetry = onRetry) { data ->
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Row {
                StatTile(
                    label = stringResource(R.string.domains_tile_uniques),
                    value = formatCompactCount(data.uniques),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = stringResource(R.string.domains_tile_requests),
                    value = formatCompactCount(data.requests),
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
            }
            Row(modifier = Modifier.padding(top = 8.dp)) {
                StatTile(
                    label = stringResource(R.string.domains_tile_cached),
                    value = data.percentCached?.let { formatPercent(it) } ?: stringResource(R.string.domains_no_value),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = stringResource(R.string.domains_tile_bytes),
                    value = formatBytes(data.bytes),
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
            }
            LineChart(
                points = data.series.map { ChartPoint(label = formatChartTimeLabel(it.datetime), value = it.requests.toDouble()) },
                title = stringResource(R.string.domains_chart_requests),
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

/** Development mode with its countdown. Turning it on or off is not destructive, so it does not ask first. */
@Composable
fun DevelopmentModeRow(status: UiState<DevModeStatus>, busy: Boolean, onSet: (Boolean) -> Unit) {
    val title = stringResource(R.string.domains_dev_mode)
    UiStateContent(state = status) { dev ->
        val supporting = when {
            dev.on && dev.remainingSeconds != null ->
                stringResource(R.string.domains_dev_mode_remaining, formatDuration(dev.remainingSeconds * MILLIS_PER_SECOND))
            dev.on -> stringResource(R.string.domains_dev_mode_on)
            else -> stringResource(R.string.domains_dev_mode_off)
        }
        ListRow(
            headline = title,
            supporting = supporting,
            trailing = {
                Switch(
                    checked = dev.on,
                    onCheckedChange = onSet,
                    enabled = dev.editable && !busy,
                    modifier = Modifier.semantics { contentDescription = title },
                )
            },
        )
    }
}

/** Worker routes attached to the zone. A route with a Worker opens that Worker; one without shows its pattern only. */
@Composable
fun ZoneRoutesSection(routes: UiState<List<WorkerRoute>>, onOpenWorker: (String) -> Unit, onRetry: () -> Unit) {
    SectionHeader(title = stringResource(R.string.domains_section_routes))
    UiStateContent(state = routes, onRetry = onRetry) { list ->
        if (list.isEmpty()) {
            Text(stringResource(R.string.domains_routes_empty), modifier = Modifier.padding(horizontal = CARD_GUTTER))
        } else {
            SectionCard(rows = list.map { route -> routeRow(route, onOpenWorker) }, modifier = Modifier.padding(horizontal = CARD_GUTTER))
        }
    }
}

private fun routeRow(route: WorkerRoute, onOpenWorker: (String) -> Unit): @Composable () -> Unit = {
    val script = route.script
    ListRow(
        headline = route.pattern,
        supporting = script ?: stringResource(R.string.domains_route_no_worker),
        onClick = script?.let { name -> { onOpenWorker(name) } },
    )
}

/** Registrar details, or a plain line when the domain was not bought through Cloudflare Registrar. */
@Composable
fun ZoneRegistrarSection(registrar: UiState<RegistrarRegistration?>, onRetry: () -> Unit) {
    SectionHeader(title = stringResource(R.string.domains_section_registrar))
    UiStateContent(state = registrar, onRetry = onRetry) { reg ->
        if (reg == null) {
            Text(stringResource(R.string.domains_registrar_none), modifier = Modifier.padding(horizontal = CARD_GUTTER))
        } else {
            val none = stringResource(R.string.domains_no_value)
            val rows: List<@Composable () -> Unit> = listOf(
                { KeyValueRow(stringResource(R.string.domains_kv_status), zoneStatusLabel(reg.status)) },
                { KeyValueRow(stringResource(R.string.domains_kv_expires), formatIsoDate(reg.expiresAt).orEmpty().ifEmpty { none }) },
                { KeyValueRow(stringResource(R.string.domains_kv_auto_renew), yesNo(reg.autoRenew)) },
                { KeyValueRow(stringResource(R.string.domains_kv_locked), yesNo(reg.locked)) },
                { KeyValueRow(stringResource(R.string.domains_kv_privacy), privacyLabel(reg.privacyMode)) },
            )
            SectionCard(rows = rows, modifier = Modifier.padding(horizontal = CARD_GUTTER))
        }
    }
}

@Composable
private fun privacyLabel(raw: String?): String = when (raw?.lowercase()) {
    PRIVACY_REDACTION -> stringResource(R.string.domains_privacy_redacted)
    null, "" -> stringResource(R.string.domains_no_value)
    else -> humanizeId(raw)
}

@Composable
private fun yesNo(value: Boolean?): String = when (value) {
    true -> stringResource(R.string.domains_yes)
    false -> stringResource(R.string.domains_no)
    null -> stringResource(R.string.domains_no_value)
}

/** Gutter of a grouped card on the overview and DNS screens: rows inside a card are indented by their own padding. */
internal val CARD_GUTTER = 16.dp

private const val PRIVACY_REDACTION = "redaction"
