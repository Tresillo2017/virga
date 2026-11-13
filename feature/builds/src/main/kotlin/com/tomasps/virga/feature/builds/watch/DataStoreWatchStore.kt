package com.tomasps.virga.feature.builds.watch

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.watchDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "virga_watched_builds",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

private val WATCHED_KEY = stringPreferencesKey("watched_builds")
private val STORE_JSON = Json { ignoreUnknownKeys = true }

@Serializable
private data class StoredWatch(
    val accountId: String,
    val scriptName: String,
    val buildUuid: String,
    val startedAtMillis: Long,
)

/**
 * Watched build ids in DataStore, so they survive process death. Holds only ids and names, never the token. A
 * corrupt value reads as an empty list: the watch list is a convenience, and losing it must not stop the app.
 */
class DataStoreWatchStore(private val context: Context) : WatchStore {

    override suspend fun load(): List<WatchEntry> = try {
        val raw = context.watchDataStore.data.first()[WATCHED_KEY]
        if (raw == null) {
            emptyList()
        } else {
            STORE_JSON.decodeFromString(ListSerializer(StoredWatch.serializer()), raw)
                .map { WatchEntry(it.accountId, it.scriptName, it.buildUuid, it.startedAtMillis) }
        }
    } catch (corrupt: Exception) {
        emptyList()
    }

    override suspend fun save(entries: List<WatchEntry>) {
        val encoded = STORE_JSON.encodeToString(
            ListSerializer(StoredWatch.serializer()),
            entries.map { StoredWatch(it.accountId, it.scriptName, it.buildUuid, it.startedAtMillis) },
        )
        context.watchDataStore.edit { it[WATCHED_KEY] = encoded }
    }
}
