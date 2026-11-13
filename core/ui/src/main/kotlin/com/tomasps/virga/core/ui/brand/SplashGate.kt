package com.tomasps.virga.core.ui.brand

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.tomasps.virga.core.ui.theme.VirgaTheme
import kotlinx.coroutines.flow.StateFlow

/**
 * The one-time Compose splash, then [afterSplash] for good. Shown once per app launch, and it survives recreation:
 * a recreated activity (rotation, font scale, theme) resumes at the finished picture rather than replaying the
 * animation from the start or drawing a blank screen while it waits for the system splash.
 *
 * [splashVisible] is true once the system splash has handed over; the Compose clock starts then. [onSplashStarted]
 * runs on the first frame of the Compose splash, which is when the system splash may be released.
 */
@Composable
fun SplashGate(
    splashVisible: StateFlow<Boolean>,
    onSplashStarted: () -> Unit,
    afterSplash: @Composable () -> Unit,
) {
    var animationDone by rememberSaveable { mutableStateOf(false) }
    // Saved with the instance state, so a recreated activity knows the animation already began.
    var animationStarted by rememberSaveable { mutableStateOf(false) }
    if (animationDone) {
        afterSplash()
        return
    }
    // Read once per composition instance: a recreated activity starts here with the saved value, not with false.
    val resumeAtFinalFrame = remember { animationStarted }
    val visible by splashVisible.collectAsState()
    DarkSplash(
        holdFinalFrame = resumeAtFinalFrame,
        startWhen = visible,
        onStarted = {
            animationStarted = true
            onSplashStarted()
        },
        onFinished = { animationDone = true },
    )
}

/** The animated splash on the dark palette, whatever the app theme, so the hand-off from the system splash matches. */
@Composable
fun DarkSplash(
    holdFinalFrame: Boolean,
    startWhen: Boolean = true,
    onStarted: () -> Unit = {},
    onFinished: () -> Unit = {},
) {
    VirgaTheme(darkTheme = true) {
        AnimatedSplash(
            modifier = Modifier.fillMaxSize(),
            holdFinalFrame = holdFinalFrame,
            startWhen = startWhen,
            onStarted = onStarted,
            onFinished = onFinished,
        )
    }
}
