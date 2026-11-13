package com.tomasps.virga.feature.d1.list

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.tomasps.virga.feature.d1.data.D1Limits
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * A database this device deleted, kept so it can be restored while the API still allows it. The API only has the
 * uuid after deletion, so the name and time are remembered here.
 */
@Serializable
data class RecentlyDeletedDatabase(
    val uuid: String,
    val name: String,
    @SerialName("deleted_at_millis") val deletedAtMillis: Long,
)

/** Where recently deleted databases are remembered, per account. Stored on this device only. */
interface RecentlyDeletedLog {
    fun observe(accountId: String): Flow<List<RecentlyDeletedDatabase>>
    suspend fun remember(accountId: String, entry: RecentlyDeletedDatabase, nowMillis: Long)
    suspend fun forget(accountId: String, uuid: String)
}

/** Pure rules for the local list: what can still be restored, and how an entry is added without duplicates. */
internal object RecentlyDeletedRules {

    fun restorable(entries: List<RecentlyDeletedDatabase>, nowMillis: Long): List<RecentlyDeletedDatabase> =
        entries.filter { nowMillis - it.deletedAtMillis < D1Limits.UNDELETE_WINDOW_MILLIS }

    fun added(entries: List<RecentlyDeletedDatabase>, entry: RecentlyDeletedDatabase, nowMillis: Long): List<RecentlyDeletedDatabase> =
        restorable(entries.filterNot { it.uuid == entry.uuid } + entry, nowMillis)

    fun without(entries: List<RecentlyDeletedDatabase>, uuid: String): List<RecentlyDeletedDatabase> =
        entries.filterNot { it.uuid == uuid }

    /** The moment after which the API refuses to restore the database. */
    fun restoreDeadlineMillis(entry: RecentlyDeletedDatabase): Long = entry.deletedAtMillis + D1Limits.UNDELETE_WINDOW_MILLIS
}

/** JSON for the stored list. Unreadable content reads as an empty list, never as an error. */
internal object RecentlyDeletedCodec {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(RecentlyDeletedDatabase.serializer())

    fun decode(raw: String?): List<RecentlyDeletedDatabase> =
        raw?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()

    fun encode(entries: List<RecentlyDeletedDatabase>): String = json.encodeToString(serializer, entries)
}

/** [RecentlyDeletedLog] backed by DataStore. A failed write is dropped, so a storage problem never blocks the list. */
class DataStoreRecentlyDeletedLog(private val store: DataStore<Preferences>) : RecentlyDeletedLog {

    override fun observe(accountId: String): Flow<List<RecentlyDeletedDatabase>> = store.data
        .map { prefs -> RecentlyDeletedCodec.decode(prefs[keyFor(accountId)]) }
        .catch { emit(emptyList()) }

    override suspend fun remember(accountId: String, entry: RecentlyDeletedDatabase, nowMillis: Long) {
        runCatching {
            store.edit { prefs ->
                val current = RecentlyDeletedCodec.decode(prefs[keyFor(accountId)])
                prefs[keyFor(accountId)] = RecentlyDeletedCodec.encode(RecentlyDeletedRules.added(current, entry, nowMillis))
            }
        }
    }

    override suspend fun forget(accountId: String, uuid: String) {
        runCatching {
            store.edit { prefs ->
                val current = RecentlyDeletedCodec.decode(prefs[keyFor(accountId)])
                prefs[keyFor(accountId)] = RecentlyDeletedCodec.encode(RecentlyDeletedRules.without(current, uuid))
            }
        }
    }

    private fun keyFor(accountId: String) = stringPreferencesKey(KEY_PREFIX + accountId)

    private companion object {
        const val KEY_PREFIX = "d1_recently_deleted_"
    }
}
