package com.tomasps.virga.wear.surfaces

import android.util.Log
import com.tomasps.virga.core.common.builds.BuildSummary
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.wear.builds.WearBuilds
import com.tomasps.virga.wear.session.WatchSessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant

enum class SurfaceStatus { Fresh, Stale, NotConnected, Reconnect }

/** What a surface renders. [fetchedAt] is when [latest] was fetched; both are null when there is no data. */
data class SurfaceData(val latest: BuildSummary?, val fetchedAt: Instant?, val status: SurfaceStatus)

internal const val SESSION_SETTLE_TIMEOUT_MILLIS = 5_000L
private const val TAG = "VirgaSurface"

/**
 * One refresh for the tile and the complication; concurrent callers take turns, so two surfaces never run two fetches
 * at once. A session that is not connected never reaches the network. Any failure, including a single failed Worker
 * (see [WearBuilds.latest]), falls back to the cached build of the same account and mode, marked
 * [SurfaceStatus.Stale]. If the session is not the same Connected value after the call (a 401 wiped it, or the
 * account or demo mode switched), the cache is dropped and the result is [SurfaceStatus.Reconnect].
 */
class BuildSurfaceRefresher(
    private val source: WearBuilds,
    private val session: StateFlow<WatchSessionState>,
    private val cache: LatestBuildCache,
    private val warn: (String) -> Unit = { Log.e(TAG, it) },
    private val clock: () -> Instant,
) {
    private val lock = Mutex()

    suspend fun refresh(): SurfaceData = lock.withLock { refreshLocked() }

    private suspend fun refreshLocked(): SurfaceData {
        val connected = when (val state = settledSession()) {
            is WatchSessionState.Connected -> state
            WatchSessionState.Stale -> return reconnect()
            WatchSessionState.NotConnected -> return notConnected()
            // Restoring never settled: the session is unknown, so show nothing but keep the cache.
            WatchSessionState.Restoring -> return SurfaceData(null, null, SurfaceStatus.NotConnected)
        }
        val result = source.latest(connected.accountId)
        if (session.value != connected) return reconnect()
        return when (result) {
            is ApiResult.Success -> saveLatest(connected, result.value)
            is ApiResult.Failure -> lastKnown(connected)
        }
    }

    /** The process may start a surface before the app has restored the session, so wait briefly for it to settle. */
    private suspend fun settledSession(): WatchSessionState =
        withTimeoutOrNull(SESSION_SETTLE_TIMEOUT_MILLIS) { session.first { it !is WatchSessionState.Restoring } }
            ?: WatchSessionState.Restoring

    private suspend fun saveLatest(session: WatchSessionState.Connected, newest: BuildSummary?): SurfaceData {
        val now = clock()
        if (newest == null) {
            safely("clear") { cache.clear() }
        } else {
            // A cache write failure must not throw away a successful fetch.
            safely("save") { cache.save(session.accountId, session.isDemo, newest, now) }
        }
        return SurfaceData(newest, now, SurfaceStatus.Fresh)
    }

    private suspend fun lastKnown(session: WatchSessionState.Connected): SurfaceData {
        val cached = cache.load()
        if (cached == null || cached.accountId != session.accountId || cached.isDemo != session.isDemo) {
            if (cached != null) safely("clear") { cache.clear() }
            return SurfaceData(null, null, SurfaceStatus.Stale)
        }
        return SurfaceData(cached.build, cached.fetchedAt, SurfaceStatus.Stale)
    }

    private suspend fun notConnected(): SurfaceData {
        safely("clear") { cache.clear() }
        return SurfaceData(null, null, SurfaceStatus.NotConnected)
    }

    private suspend fun reconnect(): SurfaceData {
        safely("clear") { cache.clear() }
        return SurfaceData(null, null, SurfaceStatus.Reconnect)
    }

    private suspend fun safely(step: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
            warn("Cache $step failed: ${error::class.java.name}")
        }
    }
}
