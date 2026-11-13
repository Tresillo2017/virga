package com.tomasps.virga.core.ui.theme

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.ui.brand.VirgaMark

@Composable
private fun ThemeSample() {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(16.dp)) {
            Text("Display", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            Text("1,284,905", style = statValueStyle(MaterialTheme.typography.headlineMedium), color = MaterialTheme.colorScheme.primary)
            Text("Body text on the surface", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            VirgaMark(modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun ThemeDarkPreview() {
    VirgaTheme(darkTheme = true) { ThemeSample() }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun ThemeLightPreview() {
    VirgaTheme(darkTheme = false) { ThemeSample() }
}
