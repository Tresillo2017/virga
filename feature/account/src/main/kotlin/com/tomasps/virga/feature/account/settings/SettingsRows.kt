@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.tomasps.virga.feature.account.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.ui.components.ListRow

private val ROW_PADDING = 16.dp
private val ICON_SIZE = 24.dp
/** Side padding inside a theme choice; the M3 default (24dp) wraps "Follow system" at a third of a phone's width. */
private val TOGGLE_HORIZONTAL_PADDING = 8.dp

/**
 * A labelled switch row. The whole row toggles, and the row itself carries the switch role, so a screen reader reads
 * the label and the state together. The switch is drawn without its own handler.
 */
@Composable
fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector,
    supporting: String? = null,
    enabled: Boolean = true,
) {
    ListRow(
        headline = label,
        supporting = supporting,
        leading = { SettingsIcon(icon) },
        modifier = Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        ),
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
                thumbContent = {
                    Icon(
                        imageVector = if (checked) Icons.Filled.Check else Icons.Filled.Close,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                    )
                },
            )
        },
    )
}

/**
 * The appearance choice as one connected group of toggle buttons. Each button is a radio button for the screen reader,
 * and the group takes the full row width so the three labels share it evenly.
 */
@Composable
fun ThemeChoiceGroup(
    selected: ThemeMode,
    choices: List<Pair<ThemeMode, String>>,
    onSelected: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(ROW_PADDING)
            // One choice of three: screen readers announce the buttons as a group of radio buttons.
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        choices.forEachIndexed { index, (mode, label) ->
            ToggleButton(
                checked = mode == selected,
                onCheckedChange = { onSelected(mode) },
                shapes = connectedShapesAt(index, lastIndex = choices.lastIndex),
                // Unselected choices sit on the card's own container colour, so they take the next tone up to read as
                // buttons. Tighter side padding keeps "Follow system" on one line in a third of a phone's width.
                colors = ToggleButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
                contentPadding = PaddingValues(horizontal = TOGGLE_HORIZONTAL_PADDING),
                modifier = Modifier
                    .weight(1f)
                    .semantics { role = Role.RadioButton },
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun connectedShapesAt(index: Int, lastIndex: Int): ToggleButtonShapes = when (index) {
    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
    lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
}

/** A leading icon for a settings row, tinted with the primary role. */
@Composable
fun SettingsIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(ICON_SIZE),
        tint = MaterialTheme.colorScheme.primary,
    )
}
