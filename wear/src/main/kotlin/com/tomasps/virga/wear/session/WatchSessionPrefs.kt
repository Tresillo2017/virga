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

    /**
     * The phone's sentAtMillis of the last item applied, used only to recognise that same item again (never to order
     * items or to judge staleness). It is not secret and survives [clear].
     */
    suspend fun lastAppliedItemStamp(): Long? = store.data.first()[ITEM_STAMP_KEY]

    suspend fun write(lastCheckInMillis: Long, selectedAccountId: String?, accountIds: Set<String>, itemStamp: Long) {
        store.edit { prefs ->
            prefs[ITEM_STAMP_KEY] = itemStamp
            prefs[CHECK_IN_KEY] = lastCheckInMillis
            if (selectedAccountId == null) prefs.remove(SELECTED_KEY) else prefs[SELECTED_KEY] = selectedAccountId
            prefs[ACCOUNT_IDS_KEY] = accountIds
        }
    }

    /** Removes only the check-in, so a partially written handoff restores as Stale instead of Connected. */
    suspend fun clearCheckIn() {
        store.edit { it.remove(CHECK_IN_KEY) }
    }

    /** Removes the session metadata. The item stamp stays, so the item just dismissed or rejected is not re-applied. */
    suspend fun clear() {
        store.edit { prefs ->
            val stamp = prefs[ITEM_STAMP_KEY]
            prefs.clear()
            if (stamp != null) prefs[ITEM_STAMP_KEY] = stamp
        }
    }

    private companion object {
        val CHECK_IN_KEY = longPreferencesKey("last_check_in_millis")
        val SELECTED_KEY = stringPreferencesKey("selected_account_id")
        val ACCOUNT_IDS_KEY = stringSetPreferencesKey("account_ids")
        val ITEM_STAMP_KEY = longPreferencesKey("last_applied_item_stamp")
    }
}
