package com.tomasps.virga.feature.builds.watch

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Saved watches survive a restart: the DataStore round trip, on the JVM with a real Context. */
@RunWith(RobolectricTestRunner::class)
class DataStoreWatchStoreTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    /** The DataStore is process-wide, so each test starts from an empty saved list. */
    @Before
    fun clearSavedWatches() = runBlocking {
        DataStoreWatchStore(context).save(emptyList())
    }

    @Test
    fun `saved watches come back with every field, in order`() = runBlocking {
        val store = DataStoreWatchStore(context)
        val entries = listOf(
            WatchEntry("acc-1", "edge-api", "build-1", startedAtMillis = 1_000L),
            WatchEntry("acc-2", "api/v2 \"quoted\"", "build-2", startedAtMillis = 2_000L),
        )

        store.save(entries)

        assertEquals(entries, DataStoreWatchStore(context).load())
    }

    @Test
    fun `saving an empty list clears the saved watches`() = runBlocking {
        val store = DataStoreWatchStore(context)
        store.save(listOf(WatchEntry("acc-1", "edge-api", "build-1", startedAtMillis = 1_000L)))

        store.save(emptyList())

        assertTrue(store.load().isEmpty())
    }
}
