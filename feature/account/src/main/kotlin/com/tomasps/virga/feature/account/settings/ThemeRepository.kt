package com.tomasps.virga.feature.account.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Per-device appearance preference. Not account data, so it does not live in core:auth. */
class ThemeRepository(private val store: DataStore<Preferences>) {

    val mode: Flow<ThemeMode> = store.data.map { prefs -> ThemeMode.fromStorage(prefs[THEME_KEY]) }

    suspend fun set(mode: ThemeMode) {
        store.edit { prefs -> prefs[THEME_KEY] = mode.storageValue }
    }

    private companion object {
        val THEME_KEY = stringPreferencesKey("theme_mode")
    }
}
