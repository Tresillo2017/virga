package com.tomasps.virga.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** True when the colour is closer to black than white; used to choose the dark or light brand palette. */
fun Color.isDarkTone(): Boolean = luminance() < LIGHT_LUMINANCE_THRESHOLD

private const val LIGHT_LUMINANCE_THRESHOLD = 0.5f
