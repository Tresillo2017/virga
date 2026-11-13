package com.tomasps.virga.feature.d1.sql

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private const val KEY_PREFIX = "d1_console_history_"

/**
 * Per-device console history, one list per database. Stored locally only: it never reaches Cloudflare. A failed
 * read yields an empty history and a failed write is dropped, so a storage problem never blocks the console.
 */
class QueryHistoryStore(private val store: DataStore<Preferences>) {

    fun observe(databaseId: String): Flow<List<String>> = store.data
        .map { prefs -> QueryHistory.decode(prefs[keyFor(databaseId)]) }
        .catch { emit(emptyList()) }

    suspend fun record(databaseId: String, sql: String) {
        runCatching {
            store.edit { prefs ->
                val current = QueryHistory.decode(prefs[keyFor(databaseId)])
                prefs[keyFor(databaseId)] = QueryHistory.encode(QueryHistory.add(current, sql))
            }
        }
    }

    suspend fun clear(databaseId: String) {
        runCatching { store.edit { prefs -> prefs.remove(keyFor(databaseId)) } }
    }

    private fun keyFor(databaseId: String) = stringPreferencesKey(KEY_PREFIX + databaseId)
}
