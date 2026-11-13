package com.tomasps.virga.feature.kv.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.ui.state.UiText
import com.tomasps.virga.core.ui.state.resolve
import com.tomasps.virga.feature.kv.R
import com.tomasps.virga.feature.kv.value.KvExpirationDraft
import com.tomasps.virga.feature.kv.value.KvExpirationMode
import kotlinx.coroutines.flow.SharedFlow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.launch

private val FIELD_GAP = 8.dp
private const val BYTES_PER_KIB = 1024.0
private const val KIB_PER_MIB = 1024.0

/** A local date and time for an epoch second, in the device's zone. */
internal fun formatEpochSeconds(epochSeconds: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(Locale.getDefault())
        .format(Instant.ofEpochSecond(epochSeconds).atZone(zone))

/** Human size: bytes, KiB or MiB. Used for values and for the limits shown next to inputs. */
internal fun formatBytes(bytes: Long): String = when {
    bytes < BYTES_PER_KIB -> "$bytes B"
    bytes < BYTES_PER_KIB * KIB_PER_MIB -> String.format(Locale.getDefault(), "%.1f KiB", bytes / BYTES_PER_KIB)
    else -> String.format(Locale.getDefault(), "%.1f MiB", bytes / (BYTES_PER_KIB * KIB_PER_MIB))
}

/** Shows each one-shot message from [messages] in the snackbar, resolving its text against the current context. */
@Composable
internal fun KvMessageEffect(messages: SharedFlow<UiText>, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    LaunchedEffect(messages) {
        messages.collect { text -> launch { snackbar.showSnackbar(text.resolve(context)) } }
    }
}

/** The three expiration choices with their inputs. The 60 second rule is checked when the change is saved. */
@Composable
internal fun KvExpirationFields(
    draft: KvExpirationDraft,
    onChange: (KvExpirationDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        KvExpirationMode.entries.forEach { mode ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = draft.mode == mode,
                        role = Role.RadioButton,
                        onClick = { onChange(draft.copy(mode = mode)) },
                    ),
            ) {
                RadioButton(selected = draft.mode == mode, onClick = null)
                Text(stringResource(modeLabel(mode)), modifier = Modifier.padding(start = 8.dp))
            }
        }
        if (draft.mode != KvExpirationMode.NONE) {
            OutlinedTextField(
                value = draft.input,
                onValueChange = { onChange(draft.copy(input = it)) },
                modifier = Modifier.fillMaxWidth().padding(top = FIELD_GAP),
                singleLine = draft.mode == KvExpirationMode.TTL,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (draft.mode == KvExpirationMode.TTL) KeyboardType.Number else KeyboardType.Text,
                ),
                label = {
                    Text(
                        stringResource(
                            if (draft.mode == KvExpirationMode.TTL) R.string.kv_expiration_ttl_label else R.string.kv_expiration_absolute_label,
                        ),
                    )
                },
                supportingText = {
                    Text(
                        stringResource(
                            if (draft.mode == KvExpirationMode.TTL) R.string.kv_expiration_ttl_hint else R.string.kv_expiration_absolute_hint,
                        ),
                    )
                },
            )
        }
    }
}

private fun modeLabel(mode: KvExpirationMode): Int = when (mode) {
    KvExpirationMode.NONE -> R.string.kv_expiration_none
    KvExpirationMode.ABSOLUTE -> R.string.kv_expiration_absolute
    KvExpirationMode.TTL -> R.string.kv_expiration_ttl
}

/** Keeps a list rule in one place: the summary line under a key row. */
internal fun keyRowSupporting(expiresAt: Long?, hasMetadata: Boolean, expiresLabel: String, noExpiryLabel: String, metadataLabel: String): String {
    val expiry = expiresAt?.let { expiresLabel.format(formatEpochSeconds(it)) } ?: noExpiryLabel
    return if (hasMetadata) "$expiry · $metadataLabel" else expiry
}
