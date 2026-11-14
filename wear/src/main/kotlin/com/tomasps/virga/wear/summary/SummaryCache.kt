package com.tomasps.virga.wear.summary

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.Instant

/** The last good summary, the account and mode it belongs to, and when it was fetched. */
data class CachedSummary(val accountId: String, val isDemo: Boolean, val data: SummaryData, val fetchedAt: Instant)

/**
 * Keeps the last good numbers so the tile can still show something when the network fails. Holds only the two
 * counts and the account id, never a token. A store that cannot be read back reads as no cache.
 */
class SummaryCache(private val store: DataStore<Preferences>) {

    suspend fun save(accountId: String, isDemo: Boolean, data: SummaryData, fetchedAt: Instant) {
        store.edit { prefs ->
            prefs.clear()
            prefs[ACCOUNT] = accountId
            prefs[IS_DEMO] = isDemo
            prefs[REQUESTS] = data.requests
            prefs[ERRORS] = data.errors
            prefs[FETCHED_AT] = fetchedAt.toEpochMilli()
        }
    }

    suspend fun load(): CachedSummary? = try {
        read(store.data.first())
    } catch (error: CancellationException) {
        throw error
    } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
        null
    }

    suspend fun clear() {
        store.edit { it.clear() }
    }

    private fun read(prefs: Preferences): CachedSummary? = CachedSummary(
        accountId = prefs[ACCOUNT] ?: return null,
        isDemo = prefs[IS_DEMO] ?: return null,
        data = SummaryData(prefs[REQUESTS] ?: return null, prefs[ERRORS] ?: return null),
        fetchedAt = Instant.ofEpochMilli(prefs[FETCHED_AT] ?: return null),
    )

    private companion object {
        val ACCOUNT = stringPreferencesKey("account_id")
        val IS_DEMO = booleanPreferencesKey("is_demo")
        val REQUESTS = longPreferencesKey("requests")
        val ERRORS = longPreferencesKey("errors")
        val FETCHED_AT = longPreferencesKey("fetched_at")
    }
}
