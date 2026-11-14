package com.tomasps.virga.wear.summary

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant

class SummaryCacheTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val store by lazy {
        PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "s.preferences_pb") }
    }
    private val cache by lazy { SummaryCache(store) }
    private val at = Instant.parse("2026-11-14T12:00:00Z")

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun savedSummaryReadsBackWithAccountModeAndFetchTime() = runTest {
        cache.save("acc", true, SummaryData(5, 1), at)

        assertEquals(CachedSummary("acc", true, SummaryData(5, 1), at), cache.load())
    }

    @Test
    fun emptyStoreReadsAsNull() = runTest {
        assertNull(cache.load())
    }

    @Test
    fun clearedCacheReadsAsNull() = runTest {
        cache.save("acc", false, SummaryData(5, 1), at)

        cache.clear()

        assertNull(cache.load())
    }

    @Test
    fun partialStoreReadsAsNull() = runTest {
        store.edit { it[stringPreferencesKey("account_id")] = "acc"; it[longPreferencesKey("requests")] = 3 }

        assertNull(cache.load())
    }
}
