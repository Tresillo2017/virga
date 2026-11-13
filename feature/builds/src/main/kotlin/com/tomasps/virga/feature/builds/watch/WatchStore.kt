package com.tomasps.virga.feature.builds.watch

/** Durable list of watched builds. The Android implementation is DataStore; tests use an in-memory fake. */
interface WatchStore {
    /** Entries saved by an earlier process. Returns an empty list when nothing is stored or the data is unreadable. */
    suspend fun load(): List<WatchEntry>

    suspend fun save(entries: List<WatchEntry>)
}
