package com.tomasps.virga.core.ui.dialogs

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.tomasps.virga.core.ui.theme.VirgaTheme

@Preview(name = "Confirm, dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun ConfirmDialogPreview() {
    VirgaTheme(darkTheme = true) {
        ConfirmDialog(
            title = "Purge cache?",
            message = "Cached copies of this zone are removed. Requests are slower until the cache refills.",
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Preview(name = "Confirm, destructive, light", showBackground = true)
@Composable
private fun DestructiveConfirmDialogPreview() {
    VirgaTheme(darkTheme = false) {
        ConfirmDialog(
            title = "Delete DNS record?",
            message = "The record stops resolving immediately.",
            onConfirm = {},
            onDismiss = {},
            destructive = true,
        )
    }
}

@Preview(name = "Typed name", showBackground = true)
@Composable
private fun TypedNameConfirmDialogPreview() {
    VirgaTheme(darkTheme = true) {
        TypedNameConfirmDialog(
            title = "Delete Worker?",
            message = "This removes the Worker and its routes. It cannot be undone.",
            expectedName = "api-gateway",
            onConfirm = {},
            onDismiss = {},
        )
    }
}
