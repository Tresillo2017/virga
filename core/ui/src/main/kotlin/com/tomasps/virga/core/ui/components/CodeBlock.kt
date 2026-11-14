package com.tomasps.virga.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.unit.dp

private val BLOCK_PADDING = 12.dp

/**
 * Monospace, selectable block for code, logs or JSON. By default long lines scroll sideways instead of wrapping,
 * so indentation survives. Pass [wrap] for short prose-like values (a DS record) that should stay fully visible.
 */
@Composable
fun CodeBlock(
    code: String,
    modifier: Modifier = Modifier,
    wrap: Boolean = false,
) {
    SelectionContainer(modifier = modifier) {
        Text(
            text = code,
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .then(if (wrap) Modifier.fillMaxWidth() else Modifier.horizontalScroll(rememberScrollState()))
                .padding(BLOCK_PADDING),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
            softWrap = wrap,
        )
    }
}
