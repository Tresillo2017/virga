package com.tomasps.virga.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.material3.MaterialTheme
import com.tomasps.virga.R
import com.tomasps.virga.wear.present.BuildVisualState

private val RunningColor = Color(0xFF8AB4F8)
private val PassedColor = Color(0xFF81C995)
private val FailedColor = Color(0xFFF28B82)
private val CanceledColor = Color(0xFFBDC1C6)
private val UnknownColor = Color(0xFF9AA0A6)

/** The watch's theme: the Wear Material 3 defaults, which are dark and suit OLED. */
@Composable
fun VirgaWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}

fun BuildVisualState.color(): Color = when (this) {
    BuildVisualState.RUNNING -> RunningColor
    BuildVisualState.PASSED -> PassedColor
    BuildVisualState.FAILED -> FailedColor
    BuildVisualState.CANCELED -> CanceledColor
    BuildVisualState.UNKNOWN -> UnknownColor
}

@Composable
fun BuildVisualState.label(): String = stringResource(
    when (this) {
        BuildVisualState.RUNNING -> R.string.status_running
        BuildVisualState.PASSED -> R.string.status_passed
        BuildVisualState.FAILED -> R.string.status_failed
        BuildVisualState.CANCELED -> R.string.status_canceled
        BuildVisualState.UNKNOWN -> R.string.status_unknown
    },
)

@Composable
fun ErrorKind.phrase(): String = stringResource(
    when (this) {
        ErrorKind.Offline -> R.string.error_offline
        ErrorKind.TokenRejected -> R.string.error_token_rejected
        ErrorKind.RateLimited -> R.string.error_rate_limited
        ErrorKind.SomethingWrong -> R.string.error_something_wrong
    },
)
