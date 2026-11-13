package com.tomasps.virga.feature.builds.watch

import com.tomasps.virga.feature.builds.testing.FakeWatchStore
import com.tomasps.virga.feature.builds.testing.TEST_ACCOUNT
import com.tomasps.virga.feature.builds.testing.TEST_BUILD
import com.tomasps.virga.feature.builds.testing.TEST_SCRIPT
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WatchRegistryTest {

    @Test
    fun `adding a build publishes its id and persists it`() = runTest {
        val store = FakeWatchStore()
        val registry = WatchRegistry(store, this, clockMillis = { 1_000L })
        registry.restore()

        registry.add(TEST_ACCOUNT, TEST_SCRIPT, TEST_BUILD)
        advanceUntilIdle()

        assertEquals(setOf(TEST_BUILD), registry.watchedIds.first())
        assertEquals(listOf(WatchEntry(TEST_ACCOUNT, TEST_SCRIPT, TEST_BUILD, 1_000L)), store.saved)
    }

    @Test
    fun `adding the same build twice keeps the first watch`() = runTest {
        val registry = WatchRegistry(FakeWatchStore(), this, clockMillis = { 5L })

        val first = registry.add(TEST_ACCOUNT, TEST_SCRIPT, TEST_BUILD)
        val second = registry.add(TEST_ACCOUNT, TEST_SCRIPT, TEST_BUILD)

        assertEquals(first, second)
        assertEquals(1, registry.snapshot().size)
    }

    @Test
    fun `update ignores a build that was stopped in the meantime`() = runTest {
        val registry = WatchRegistry(FakeWatchStore(), this, clockMillis = { 0L })
        val watch = registry.add(TEST_ACCOUNT, TEST_SCRIPT, TEST_BUILD)
        registry.remove(TEST_BUILD)

        assertFalse(registry.update(watch.copy(backoffMillis = 9)))
        assertFalse(registry.isWatching(TEST_BUILD))
    }

    @Test
    fun `saved watches restore as paused and are not polled or shown as watched`() = runTest {
        val saved = listOf(WatchEntry(TEST_ACCOUNT, TEST_SCRIPT, TEST_BUILD, 10L))
        val registry = WatchRegistry(FakeWatchStore(saved), this, clockMillis = { 99L })

        registry.restore()
        advanceUntilIdle()

        assertTrue(registry.snapshot().isEmpty())
        assertEquals(emptySet<String>(), registry.watchedIds.first())
    }

    @Test
    fun `tapping Watch on a paused build starts a fresh session and saves it`() = runTest {
        val store = FakeWatchStore(listOf(WatchEntry(TEST_ACCOUNT, TEST_SCRIPT, TEST_BUILD, 10L)))
        val registry = WatchRegistry(store, this, clockMillis = { 500L })
        registry.restore()

        val watch = registry.add(TEST_ACCOUNT, TEST_SCRIPT, TEST_BUILD)
        advanceUntilIdle()

        assertEquals(500L, watch.startedAtMillis)
        assertEquals(setOf(TEST_BUILD), registry.watchedIds.first())
        assertEquals(listOf(WatchEntry(TEST_ACCOUNT, TEST_SCRIPT, TEST_BUILD, 500L)), store.saved)
    }

    @Test
    fun `restore drops entries older than the two hour cap`() = runTest {
        val expired = WatchEntry(TEST_ACCOUNT, TEST_SCRIPT, "old", startedAtMillis = 0L)
        val store = FakeWatchStore(listOf(expired))
        val registry = WatchRegistry(store, this, clockMillis = { WatchPolicy.MAX_WATCH_MS + 1 })

        registry.restore()
        advanceUntilIdle()

        assertTrue(store.saved.isEmpty())
    }

    @Test
    fun `a change made before restore does not overwrite the saved list`() = runTest {
        val saved = WatchEntry(TEST_ACCOUNT, TEST_SCRIPT, "saved-build", 10L)
        val store = FakeWatchStore(listOf(saved))
        val registry = WatchRegistry(store, this, clockMillis = { 20L })

        registry.add(TEST_ACCOUNT, TEST_SCRIPT, TEST_BUILD)
        advanceUntilIdle()
        assertEquals(listOf(saved), store.saved)

        registry.restore()
        advanceUntilIdle()

        assertEquals(setOf(saved, WatchEntry(TEST_ACCOUNT, TEST_SCRIPT, TEST_BUILD, 20L)), store.saved.toSet())
    }

    @Test
    fun `an unreadable store restores nothing, reports it, and saves nothing`() = runTest {
        val store = FakeWatchStore().apply { failLoad = true }
        val registry = WatchRegistry(store, this, clockMillis = { 0L })

        registry.restore()
        registry.add(TEST_ACCOUNT, TEST_SCRIPT, TEST_BUILD)
        advanceUntilIdle()

        assertTrue(registry.snapshot().size == 1)
        assertTrue(registry.storageFailed.first())
        assertTrue(store.saved.isEmpty())
    }

    @Test
    fun `pauseAll moves active watches to paused and saves them`() = runTest {
        val store = FakeWatchStore()
        val registry = WatchRegistry(store, this, clockMillis = { 7L })
        registry.restore()
        registry.add(TEST_ACCOUNT, TEST_SCRIPT, TEST_BUILD)

        registry.pauseAll()
        advanceUntilIdle()

        assertTrue(registry.snapshot().isEmpty())
        assertEquals(emptySet<String>(), registry.watchedIds.first())
        assertEquals(listOf(WatchEntry(TEST_ACCOUNT, TEST_SCRIPT, TEST_BUILD, 7L)), store.saved)
    }

    @Test
    fun `remove stops a paused build too`() = runTest {
        val store = FakeWatchStore(listOf(WatchEntry(TEST_ACCOUNT, TEST_SCRIPT, TEST_BUILD, 1L)))
        val registry = WatchRegistry(store, this, clockMillis = { 2L })
        registry.restore()
        advanceUntilIdle()

        registry.remove(TEST_BUILD)
        advanceUntilIdle()

        assertTrue(store.saved.isEmpty())
    }

    @Test
    fun `retainAccount keeps only that account, active and paused alike`() = runTest {
        val store = FakeWatchStore(listOf(WatchEntry("other-account", TEST_SCRIPT, "paused-other", 1L)))
        val registry = WatchRegistry(store, this, clockMillis = { 0L })
        registry.restore()
        registry.add(TEST_ACCOUNT, TEST_SCRIPT, "keep-me")
        registry.add("other-account", TEST_SCRIPT, "drop-me")

        registry.retainAccount(TEST_ACCOUNT)
        advanceUntilIdle()

        assertEquals(setOf("keep-me"), registry.snapshot().keys)
        assertTrue(store.saved.none { it.accountId == "other-account" })
    }

    @Test
    fun `retainAccount with no account drops everything`() = runTest {
        val registry = WatchRegistry(FakeWatchStore(), this, clockMillis = { 0L })
        registry.restore()
        registry.add(TEST_ACCOUNT, TEST_SCRIPT, TEST_BUILD)

        registry.retainAccount(null)

        assertTrue(registry.snapshot().isEmpty())
    }
}
