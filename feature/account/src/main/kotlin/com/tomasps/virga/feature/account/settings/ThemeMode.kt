package com.tomasps.virga.feature.account.settings

/** The appearance choice. [System] follows the device's dark setting. */
enum class ThemeMode(val storageValue: String) {
    System("system"),
    Light("light"),
    Dark("dark");

    companion object {
        fun fromStorage(value: String?): ThemeMode = entries.firstOrNull { it.storageValue == value } ?: System
    }
}
