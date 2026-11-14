package com.tomasps.virga.wear.surfaces

import com.tomasps.virga.wear.session.WatchSessionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/**
 * Refreshes the tiles and complication whenever the session settles into a new state (connected, a switch of
 * account, stale, wiped), so they never show "Setup" or a signed-out build for up to 15 minutes. Restoring is
 * transient and requests nothing.
 */
class SessionSurfaceUpdates(private val session: Flow<WatchSessionState>, private val updater: SurfaceUpdater) {
    suspend fun run() {
        session.filter { it != WatchSessionState.Restoring }
            .distinctUntilChanged()
            .collect { updater.requestUpdates() }
    }
}
