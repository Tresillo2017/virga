package com.tomasps.virga.wearsync

import android.content.Context

/** Remembers that the watch still has to be told to forget the credential. The flag is not secret. */
interface PendingClearStore {
    suspend fun isSet(): Boolean
    suspend fun set(value: Boolean)
}

class SharedPrefsPendingClearStore(context: Context) : PendingClearStore {
    private val prefs = context.applicationContext.getSharedPreferences("virga_wear_sync", Context.MODE_PRIVATE)

    override suspend fun isSet(): Boolean = prefs.getBoolean(KEY, false)

    override suspend fun set(value: Boolean) {
        // commit() so the flag is on disk before the Data Layer call it guards.
        prefs.edit().putBoolean(KEY, value).commit()
    }

    private companion object {
        const val KEY = "pending_clear"
    }
}
