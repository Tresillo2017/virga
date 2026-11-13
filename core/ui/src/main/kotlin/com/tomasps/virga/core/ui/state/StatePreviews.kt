package com.tomasps.virga.core.ui.state

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.tomasps.virga.core.ui.theme.VirgaTheme

private val SAMPLE_ERROR = ErrorUi(
    title = UiText.Literal("Rate limited"),
    message = UiText.Literal("Cloudflare asked us to slow down. Retrying in 30 seconds."),
    retryable = true,
)

@Preview(name = "Empty, dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun EmptyStatePreview() {
    VirgaTheme(darkTheme = true) {
        EmptyState(title = "No Pages projects", message = "Create a project in the dashboard to see it here.")
    }
}

@Preview(name = "Error, retryable", showBackground = true)
@Composable
private fun ErrorStatePreview() {
    VirgaTheme(darkTheme = true) {
        ErrorState(error = SAMPLE_ERROR, onRetry = {})
    }
}

@Preview(name = "Error, generic, light", showBackground = true)
@Composable
private fun ErrorStateGenericPreview() {
    VirgaTheme(darkTheme = false) {
        ErrorState(error = ErrorUi.Generic, onRetry = {})
    }
}

@Preview(name = "Loading", showBackground = true)
@Composable
private fun LoadingStatePreview() {
    VirgaTheme(darkTheme = true) {
        LoadingState(modifier = Modifier)
    }
}
