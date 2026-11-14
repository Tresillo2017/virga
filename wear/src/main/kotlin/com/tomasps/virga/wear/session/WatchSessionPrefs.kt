package com.tomasps.virga.wear.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.first

/** Non-secret handoff metadata. The token lives only in the CredentialVault. */
class WatchSessionPrefs(private val store: DataStore<Preferences>) {

    suspend fun lastCheckInMillis(): Long? = store.data.first()[CHECK_IN_KEY]

    suspend fun selectedAccountId(): String? = store.data.first()[SELECTED_KEY]

    suspend fun accountIds(): Set<String> = store.data.first()[ACCOUNT_IDS_KEY].orEmpty()

    suspend fun write(lastCheckInMillis: Long, selectedAccountId: String?, accountIds: Set<String>) {
        store.edit { prefs ->
            prefs[CHECK_IN_KEY] = lastCheckInMillis
            if (selectedAccountId == null) prefs.remove(SELECTED_KEY) else prefs[SELECTED_KEY] = selectedAccountId
            prefs[ACCOUNT_IDS_KEY] = accountIds
        }
    }

    suspend fun clear() {
        store.edit { it.clear() }
    }

    private companion object {
        val CHECK_IN_KEY = longPreferencesKey("last_check_in_millis")
        val SELECTED_KEY = stringPreferencesKey("selected_account_id")
        val ACCOUNT_IDS_KEY = stringSetPreferencesKey("account_ids")
    }
}
