package com.tomasps.virga.feature.account.settings

import com.tomasps.virga.core.ui.components.TimeRange

/** Every per-device preference, read in one snapshot. The defaults here are what a fresh install and a reset both show. */
data class AppPreferences(
    val themeMode: ThemeMode = ThemeMode.System,
    /** Wallpaper-based colour on Android 12 and newer. Off, so the Virga palette is the default look. */
    val dynamicColor: Boolean = false,
    /** True black surfaces in the dark theme, for OLED screens. */
    val pureBlack: Boolean = false,
    /** Marks the window secure: no screenshots, blank in Recents. */
    val blockScreenshots: Boolean = false,
    /** Asks for the device's biometric or screen lock before the app shows anything. */
    val appLock: Boolean = false,
    /** The window analytics screens open on. */
    val defaultTimeRange: TimeRange = TimeRange.TWENTY_FOUR_HOURS,
)
