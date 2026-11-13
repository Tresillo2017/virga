package com.tomasps.virga.feature.account.signin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tomasps.virga.feature.account.R

/** Expandable "How to create a token" section. The permission list is data in strings.xml, not code. */
@Composable
fun TokenGuidance(
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenTokenPage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onToggle) {
            Text(stringResource(R.string.account_guidance_title))
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
            )
        }
        if (expanded) {
            GuidanceBody(onOpenTokenPage)
        }
    }
}

@Composable
private fun GuidanceBody(onOpenTokenPage: () -> Unit) {
    val steps = stringArrayResource(R.array.account_guidance_steps)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(start = 8.dp)) {
        steps.forEach { step -> Text(step, style = MaterialTheme.typography.bodyMedium) }
        PermissionList(
            headingRes = R.string.account_guidance_permissions_heading,
            permissions = stringArrayResource(R.array.account_guidance_permissions),
        )
        PermissionList(
            headingRes = R.string.account_guidance_read_only_heading,
            permissions = stringArrayResource(R.array.account_guidance_read_only_permissions),
        )
        GuidanceNote(stringResource(R.string.account_guidance_builds_note))
        GuidanceNote(stringResource(R.string.account_guidance_r2_note))
        GuidanceNote(stringResource(R.string.account_guidance_caution))
        OutlinedButton(onClick = onOpenTokenPage, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.account_guidance_open_page))
        }
    }
}

@Composable
private fun PermissionList(headingRes: Int, permissions: Array<String>) {
    Text(
        text = stringResource(headingRes),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 8.dp),
    )
    permissions.forEach { permission ->
        Text("• $permission", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun GuidanceNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
