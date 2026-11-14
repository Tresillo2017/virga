package com.tomasps.virga.feature.account.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.tomasps.virga.core.ui.components.DefaultTimeRangeSource
import com.tomasps.virga.core.ui.components.TimeRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Per-device app preferences (appearance, analytics, privacy). Not account data, so it does not live in core:auth. */
class ThemeRepository(private val store: DataStore<Preferences>) : DefaultTimeRangeSource {

    /** Every preference in one snapshot. The other flows are views of this one. */
    val preferences: Flow<AppPreferences> = store.data.map { prefs ->
        AppPreferences(
            themeMode = ThemeMode.fromStorage(prefs[THEME_KEY]),
            dynamicColor = prefs[DYNAMIC_COLOR_KEY] ?: DEFAULTS.dynamicColor,
            pureBlack = prefs[PURE_BLACK_KEY] ?: DEFAULTS.pureBlack,
            blockScreenshots = prefs[BLOCK_SCREENSHOTS_KEY] ?: DEFAULTS.blockScreenshots,
            appLock = prefs[APP_LOCK_KEY] ?: DEFAULTS.appLock,
            defaultTimeRange = TimeRange.entries.firstOrNull { it.name == prefs[DEFAULT_TIME_RANGE_KEY] }
                ?: DEFAULTS.defaultTimeRange,
        )
    }

    val mode: Flow<ThemeMode> = preferences.map { it.themeMode }

    val dynamicColor: Flow<Boolean> = preferences.map { it.dynamicColor }

    val pureBlack: Flow<Boolean> = preferences.map { it.pureBlack }

    val blockScreenshots: Flow<Boolean> = preferences.map { it.blockScreenshots }

    val appLock: Flow<Boolean> = preferences.map { it.appLock }

    val defaultTimeRange: Flow<TimeRange> = preferences.map { it.defaultTimeRange }

    override val defaultRange: Flow<TimeRange> get() = defaultTimeRange

    suspend fun set(mode: ThemeMode) {
        store.edit { prefs -> prefs[THEME_KEY] = mode.storageValue }
    }

    suspend fun setDynamicColor(enabled: Boolean) = putBoolean(DYNAMIC_COLOR_KEY, enabled)

    suspend fun setPureBlack(enabled: Boolean) = putBoolean(PURE_BLACK_KEY, enabled)

    suspend fun setBlockScreenshots(enabled: Boolean) = putBoolean(BLOCK_SCREENSHOTS_KEY, enabled)

    suspend fun setAppLock(enabled: Boolean) = putBoolean(APP_LOCK_KEY, enabled)

    suspend fun setDefaultTimeRange(range: TimeRange) {
        store.edit { prefs -> prefs[DEFAULT_TIME_RANGE_KEY] = range.name }
    }

    /** Puts every preference back to its default, theme mode included. The session is not touched. */
    suspend fun resetAll() {
        store.edit { prefs -> prefs.clear() }
    }

    private suspend fun putBoolean(key: Preferences.Key<Boolean>, value: Boolean) {
        store.edit { prefs -> prefs[key] = value }
    }

    private companion object {
        val DEFAULTS = AppPreferences()
        val THEME_KEY = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR_KEY = booleanPreferencesKey("dynamic_color")
        val PURE_BLACK_KEY = booleanPreferencesKey("pure_black")
        val BLOCK_SCREENSHOTS_KEY = booleanPreferencesKey("block_screenshots")
        val APP_LOCK_KEY = booleanPreferencesKey("app_lock")
        val DEFAULT_TIME_RANGE_KEY = stringPreferencesKey("default_time_range")
    }
}
