package com.tomasps.virga.feature.pages.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.tomasps.virga.feature.pages.R

/** A quiet, non-blocking note that Cloudflare recommends Workers for new projects. Shown on Pages screens only. */
@Composable
fun PagesNote(modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.pages_note_recommend_workers),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
