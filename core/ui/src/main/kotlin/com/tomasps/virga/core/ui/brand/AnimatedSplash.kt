package com.tomasps.virga.core.ui.brand

import android.animation.ValueAnimator
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

private val MARK_SPLASH_SIZE = 120.dp
private val MARK_TO_WORDMARK_GAP = 16.dp
private const val NANOS_PER_MILLI = 1_000_000L

/**
 * Animated splash: the three streaks draw downward, staggered by [SplashTimeline.STAGGER_MS], then the wordmark
 * fades up. Progress is a function of time since the first drawn frame, so late frames delay the picture but
 * never skip a stage. The hand-off happens at the end of the animation or at [SplashTimeline.MAX_WAIT_MS],
 * whichever comes first.
 *
 * [onStarted] runs once, on the first frame after composition, so the system splash is released only once
 * this screen is on the display. [onFinished] runs once, at the hand-off.
 *
 * The clock does not start until [startWhen] is true (or [SplashTimeline.MAX_WAIT_FOR_START_MS] has passed), so the
 * streaks are never drawn behind the system splash.
 *
 * When system animations are off ("Remove animations"), or when [holdFinalFrame] is set, the final frame is
 * drawn at once and no callbacks are needed. The caller decides the colours: wrap this in a dark VirgaTheme so
 * it matches the system splash in every theme mode.
 */
@Composable
fun AnimatedSplash(
    modifier: Modifier = Modifier,
    holdFinalFrame: Boolean = false,
    startWhen: Boolean = true,
    onStarted: () -> Unit = {},
    onFinished: () -> Unit = {},
) {
    val reduceMotion = remember { !ValueAnimator.areAnimatorsEnabled() }
    val animate = !reduceMotion && !holdFinalFrame
    val finalElapsedMs = remember { SplashTimeline.totalMs(VirgaMarkGeometry.STREAK_COUNT).toLong() }
    var elapsedMs by remember(animate) { mutableLongStateOf(if (animate) 0L else finalElapsedMs) }
    val currentOnFinished by rememberUpdatedState(onFinished)
    val currentOnStarted by rememberUpdatedState(onStarted)
    // Read through state, not captured: the effect is keyed on [animate] only, so a plain capture would keep the
    // value from its first launch (false while the system splash is still up) and the gate would never open.
    val currentStartWhen by rememberUpdatedState(startWhen)

    LaunchedEffect(animate) {
        if (!animate) {
            currentOnStarted()
            currentOnFinished()
            return@LaunchedEffect
        }
        // The first frame after composition: the splash is on the display. Release the system splash now, because
        // its exit is what sets startWhen; waiting for startWhen first would wait on itself.
        withFrameNanos { }
        currentOnStarted()
        withTimeoutOrNull(SplashTimeline.MAX_WAIT_FOR_START_MS) { snapshotFlow { currentStartWhen }.first { it } }
        val clockStartNanos = withFrameNanos { it }
        val handOffMs = SplashTimeline.handOffMs(VirgaMarkGeometry.STREAK_COUNT).toLong()
        while (true) {
            val elapsed = withFrameNanos { (it - clockStartNanos) / NANOS_PER_MILLI }
            elapsedMs = elapsed
            if (elapsed >= handOffMs) break
        }
        currentOnFinished()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        VirgaMark(
            modifier = Modifier.size(MARK_SPLASH_SIZE),
            streakProgress = List(VirgaMarkGeometry.STREAK_COUNT) { SplashTimeline.streakProgress(it, elapsedMs) },
        )
        Spacer(Modifier.height(MARK_TO_WORDMARK_GAP))
        VirgaWordmark(alpha = SplashTimeline.wordmarkProgress(elapsedMs))
    }
}
