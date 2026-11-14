package com.tomasps.virga.wear.surfaces

import com.tomasps.virga.wear.session.WatchSessionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionSurfaceUpdatesTest {

    private val connected = WatchSessionState.Connected("a1", isDemo = false)

    @Test
    fun everySettledSessionChangeRequestsOneUpdate() = runTest(UnconfinedTestDispatcher()) {
        val session = MutableStateFlow<WatchSessionState>(WatchSessionState.Restoring)
        val updater = FakeSurfaceUpdater()
        val job = launch { SessionSurfaceUpdates(session, updater).run() }

        session.value = connected
        session.value = WatchSessionState.Stale
        session.value = WatchSessionState.NotConnected

        assertEquals(3, updater.requests)
        job.cancel()
    }

    @Test
    fun restoringRequestsNothing() = runTest(UnconfinedTestDispatcher()) {
        val session = MutableStateFlow<WatchSessionState>(WatchSessionState.Restoring)
        val updater = FakeSurfaceUpdater()
        val job = launch { SessionSurfaceUpdates(session, updater).run() }

        assertEquals(0, updater.requests)
        job.cancel()
    }

    @Test
    fun aSwitchToAnotherAccountRequestsAnUpdate() = runTest(UnconfinedTestDispatcher()) {
        val session = MutableStateFlow<WatchSessionState>(connected)
        val updater = FakeSurfaceUpdater()
        val job = launch { SessionSurfaceUpdates(session, updater).run() }

        session.value = WatchSessionState.Connected("a2", isDemo = false)

        assertEquals(2, updater.requests)
        job.cancel()
    }
}
