package com.tomasps.virga.wearsync

import android.content.Context

/**
 * Remembers that the watch still has to be told to forget the credential, and whether anything was ever published
 * (so a user without a watch never has a clear to retry). Neither flag is secret.
 */
interface PendingClearStore {
    suspend fun isSet(): Boolean
    suspend fun set(value: Boolean)
    suspend fun hasEverPublished(): Boolean
    suspend fun markPublished()
}

class SharedPrefsPendingClearStore(context: Context) : PendingClearStore {
    private val prefs = context.applicationContext.getSharedPreferences("virga_wear_sync", Context.MODE_PRIVATE)

    override suspend fun isSet(): Boolean = prefs.getBoolean(KEY, false)

    override suspend fun set(value: Boolean) {
        // commit() so the flag is on disk before the Data Layer call it guards.
        prefs.edit().putBoolean(KEY, value).commit()
    }

    override suspend fun hasEverPublished(): Boolean = prefs.getBoolean(PUBLISHED_KEY, false)

    override suspend fun markPublished() {
        prefs.edit().putBoolean(PUBLISHED_KEY, true).commit()
    }

    private companion object {
        const val KEY = "pending_clear"
        const val PUBLISHED_KEY = "ever_published"
    }
}
