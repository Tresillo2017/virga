package com.tomasps.virga.core.ui.scaffold

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.ui.theme.VirgaTheme

@Preview(name = "Screen, dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, showSystemUi = true)
@Composable
private fun ScreenScaffoldPreview() {
    VirgaTheme(darkTheme = true) {
        ScreenScaffold(title = "Workers", onBack = {}, onRefresh = {}, isRefreshing = false) { padding ->
            Text(
                text = "Scrollable content goes here.",
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .padding(16.dp),
            )
        }
    }
}
