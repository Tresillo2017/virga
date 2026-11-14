package com.tomasps.virga.shell

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MotionScheme
import androidx.compose.ui.unit.IntOffset

/** A screen slides this fraction of the container width (1 / n) while it fades: a short shared-axis move, not a full push. */
private const val SHARED_AXIS_SLIDE_DIVISOR = 10

/** Going deeper: the new screen comes in from the end edge, the old one leaves towards the start edge. */
internal fun forwardTransition(motion: MotionScheme): ContentTransform =
    (fadeIn(motion.defaultEffectsSpec()) +
        slideInHorizontally(motion.defaultSpatialSpec<IntOffset>()) { width -> width / SHARED_AXIS_SLIDE_DIVISOR })
        .togetherWith(
            fadeOut(motion.defaultEffectsSpec()) +
                slideOutHorizontally(motion.defaultSpatialSpec<IntOffset>()) { width -> -width / SHARED_AXIS_SLIDE_DIVISOR },
        )

/** Going back: the mirror of [forwardTransition]. Predictive back uses the same motion, so the preview matches the result. */
internal fun backTransition(motion: MotionScheme): ContentTransform =
    (fadeIn(motion.defaultEffectsSpec()) +
        slideInHorizontally(motion.defaultSpatialSpec<IntOffset>()) { width -> -width / SHARED_AXIS_SLIDE_DIVISOR })
        .togetherWith(
            fadeOut(motion.defaultEffectsSpec()) +
                slideOutHorizontally(motion.defaultSpatialSpec<IntOffset>()) { width -> width / SHARED_AXIS_SLIDE_DIVISOR },
        )
