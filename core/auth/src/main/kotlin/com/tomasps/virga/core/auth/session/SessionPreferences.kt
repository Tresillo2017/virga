package com.tomasps.virga.core.auth.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Account cache and selection. Holds no secret; the token lives only in the CredentialVault. */
class SessionPreferences(private val store: DataStore<Preferences>) {

    suspend fun readAccounts(): List<CloudflareAccount> {
        val raw = store.data.first()[ACCOUNTS_KEY] ?: return emptyList()
        return try {
            json.decodeFromString(ListSerializer(CloudflareAccount.serializer()), raw)
        } catch (error: SerializationException) {
            emptyList()
        }
    }

    suspend fun writeAccounts(accounts: List<CloudflareAccount>) {
        val raw = json.encodeToString(ListSerializer(CloudflareAccount.serializer()), accounts)
        store.edit { it[ACCOUNTS_KEY] = raw }
    }

    suspend fun readSelectedAccountId(): String? = store.data.first()[SELECTED_KEY]

    suspend fun writeSelectedAccountId(accountId: String?) {
        store.edit { prefs ->
            if (accountId == null) prefs.remove(SELECTED_KEY) else prefs[SELECTED_KEY] = accountId
        }
    }

    suspend fun clear() {
        store.edit { prefs ->
            prefs.remove(ACCOUNTS_KEY)
            prefs.remove(SELECTED_KEY)
        }
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        val ACCOUNTS_KEY = stringPreferencesKey("accounts_json")
        val SELECTED_KEY = stringPreferencesKey("selected_account_id")
    }
}
