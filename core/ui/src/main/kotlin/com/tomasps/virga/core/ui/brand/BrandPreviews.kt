package com.tomasps.virga.core.ui.brand

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tomasps.virga.core.ui.theme.VirgaTheme

@Preview(name = "Mark, dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun VirgaMarkDarkPreview() {
    VirgaTheme(darkTheme = true) {
        VirgaMark(modifier = Modifier.padding(16.dp))
    }
}

@Preview(name = "Mark, light", showBackground = true)
@Composable
private fun VirgaMarkLightPreview() {
    VirgaTheme(darkTheme = false) {
        VirgaMark(modifier = Modifier.padding(16.dp))
    }
}

@Preview(name = "Mark, partly drawn", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VirgaMarkPartialPreview() {
    VirgaTheme(darkTheme = true) {
        VirgaMark(modifier = Modifier.padding(16.dp), streakProgress = listOf(1f, 0.5f, 0f))
    }
}

@Preview(name = "Lockup", showBackground = true)
@Composable
private fun VirgaLockupPreview() {
    VirgaTheme(darkTheme = true) {
        VirgaLockup(modifier = Modifier.padding(16.dp))
    }
}

@Preview(name = "Splash final frame", showBackground = true, showSystemUi = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AnimatedSplashPreview() {
    VirgaTheme(darkTheme = true) {
        AnimatedSplash()
    }
}

@Preview(name = "Wordmark", showBackground = true)
@Composable
private fun VirgaWordmarkPreview() {
    VirgaTheme(darkTheme = true) {
        Column(Modifier.padding(16.dp)) {
            VirgaWordmark()
            VirgaWordmark(alpha = 0.4f)
        }
    }
}
