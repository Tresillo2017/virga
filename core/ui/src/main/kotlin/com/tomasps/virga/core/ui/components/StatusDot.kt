package com.tomasps.virga.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.ui.R
import com.tomasps.virga.core.ui.theme.LocalVirgaSemanticColors

/** Health or progress of a resource. The colour is never the only signal: the description says it too. */
enum class StatusTone { Success, Warning, Error, Pending, Neutral }

private val DOT_SIZE = 8.dp
private val HALO_SIZE = 14.dp
private const val HALO_ALPHA = 0.25f

/** Small coloured dot with a spoken status. [contentDescription] overrides the default for the tone. */
@Composable
fun StatusDot(
    tone: StatusTone,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val description = contentDescription ?: stringResource(tone.labelRes())
    val color = tone.color()
    Box(
        modifier = modifier
            // Same footprint for every tone, so the text beside a dot lines up across rows.
            .size(HALO_SIZE)
            .semantics { this.contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (tone == StatusTone.Pending) {
            Box(Modifier.size(HALO_SIZE).background(color.copy(alpha = HALO_ALPHA), CircleShape))
        }
        Box(Modifier.size(DOT_SIZE).background(color, CircleShape))
    }
}

private fun StatusTone.labelRes(): Int = when (this) {
    StatusTone.Success -> R.string.core_ui_status_success
    StatusTone.Warning -> R.string.core_ui_status_warning
    StatusTone.Error -> R.string.core_ui_status_error
    StatusTone.Pending -> R.string.core_ui_status_pending
    StatusTone.Neutral -> R.string.core_ui_status_neutral
}

@Composable
private fun StatusTone.color(): Color = when (this) {
    StatusTone.Success -> LocalVirgaSemanticColors.current.success
    StatusTone.Warning -> LocalVirgaSemanticColors.current.warning
    StatusTone.Error -> MaterialTheme.colorScheme.error
    StatusTone.Pending -> MaterialTheme.colorScheme.primary
    StatusTone.Neutral -> LocalVirgaSemanticColors.current.neutral
}
