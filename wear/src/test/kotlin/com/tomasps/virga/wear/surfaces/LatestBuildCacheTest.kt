package com.tomasps.virga.wear.surfaces

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.tomasps.virga.core.common.builds.BuildOutcome
import com.tomasps.virga.core.common.builds.BuildStatus
import com.tomasps.virga.core.common.builds.BuildSummary
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

class LatestBuildCacheTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val fetchedAt = Instant.parse("2026-11-14T12:00:00Z")
    private val build = BuildSummary(
        buildUuid = "b-1",
        scriptName = "checkout",
        workerTag = "tag-1",
        status = BuildStatus.STOPPED,
        outcome = BuildOutcome.FAIL,
        branch = "main",
        commitHash = "abc",
        commitMessage = "secret message",
        author = "someone",
        createdOn = Instant.parse("2026-11-14T10:00:00Z"),
        startedOn = Instant.parse("2026-11-14T10:01:00Z"),
        stoppedOn = Instant.parse("2026-11-14T10:05:00Z"),
    )

    @After
    fun tearDown() = scope.cancel()

    private fun store(file: File = File(folder.root, "cache.preferences_pb")): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope) { file }

    @Test
    fun loadOnEmptyStoreIsNull() = runTest {
        assertNull(LatestBuildCache(store()).load())
    }

    @Test
    fun savedBuildRoundTripsWithAccountAndFetchTime() = runTest {
        val cache = LatestBuildCache(store())

        cache.save("acc-1", build, fetchedAt)
        val loaded = cache.load()!!

        assertEquals("acc-1", loaded.accountId)
        assertEquals(fetchedAt, loaded.fetchedAt)
        assertEquals(build.copy(commitHash = null, commitMessage = null, author = null), loaded.build)
    }

    @Test
    fun optionalFieldsStayNull() = runTest {
        val cache = LatestBuildCache(store())
        val bare = BuildSummary(
            buildUuid = "b-2", scriptName = "api", status = BuildStatus.RUNNING,
            createdOn = Instant.parse("2026-11-14T11:00:00Z"),
        )

        cache.save("acc-1", bare, fetchedAt)

        assertEquals(bare, cache.load()!!.build)
    }

    @Test
    fun clearRemovesTheBuild() = runTest {
        val cache = LatestBuildCache(store())
        cache.save("acc-1", build, fetchedAt)

        cache.clear()

        assertNull(cache.load())
    }

    @Test
    fun corruptStoredValuesReadAsNull() = runTest {
        val store = store()
        val cache = LatestBuildCache(store)
        cache.save("acc-1", build, fetchedAt)
        store.edit { it[stringPreferencesKey("created_on")] = "not-a-date" }

        assertNull(cache.load())
    }

    @Test
    fun corruptFileReadsAsNull() = runTest {
        val file = File(folder.root, "bad.preferences_pb").apply { writeBytes(byteArrayOf(1, 2, 3, 4, 5)) }

        assertNull(LatestBuildCache(store(file)).load())
    }
}
