package com.tomasps.virga.core.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.ui.theme.VirgaTheme

@Preview(name = "Tab row, dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun VirgaTabRowPreview() {
    VirgaTheme(darkTheme = true) {
        VirgaTabRow(
            tabs = listOf("Overview", "Metrics", "Deployments", "Logs", "Domains", "Settings"),
            selectedIndex = 1,
            onSelect = {},
        )
    }
}

@Preview(name = "Load more states", showBackground = true)
@Composable
private fun LoadMoreFooterPreview() {
    VirgaTheme(darkTheme = true) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            LoadMoreFooter(state = LoadMoreState.AVAILABLE, onLoadMore = {})
            LoadMoreFooter(state = LoadMoreState.LOADING, onLoadMore = {})
            LoadMoreFooter(state = LoadMoreState.FAILED, onLoadMore = {})
        }
    }
}

@Preview(name = "Destructive menu item, dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun DestructiveMenuItemPreview() {
    VirgaTheme(darkTheme = true) {
        Column(Modifier.padding(16.dp)) {
            DestructiveMenuItem(text = "Delete worker", onClick = {})
        }
    }
}
