package com.tomasps.virga.wear.surfaces

import com.tomasps.virga.core.common.builds.BuildSummary
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.wear.builds.WearBuilds
import com.tomasps.virga.wear.session.WatchSessionState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant

enum class SurfaceStatus { Fresh, Stale, NotConnected, Reconnect }

/** What a surface renders. [fetchedAt] is when [latest] was fetched; both are null when there is no data. */
data class SurfaceData(val latest: BuildSummary?, val fetchedAt: Instant?, val status: SurfaceStatus)

internal const val SESSION_SETTLE_TIMEOUT_MILLIS = 5_000L

/**
 * One refresh for the tile and the complication. A session that is not connected never reaches the network. Any
 * failure falls back to the cached build of the same account, marked [SurfaceStatus.Stale]. If the session is no
 * longer connected after the call (a 401 wipes it inside [WearBuilds]), the cache is dropped and the result is
 * [SurfaceStatus.Reconnect].
 */
class BuildSurfaceRefresher(
    private val source: WearBuilds,
    private val session: StateFlow<WatchSessionState>,
    private val cache: LatestBuildCache,
    private val clock: () -> Instant,
) {
    suspend fun refresh(): SurfaceData {
        val connected = when (val state = settledSession()) {
            is WatchSessionState.Connected -> state
            WatchSessionState.Stale -> return reconnect()
            else -> return notConnected()
        }
        val result = source.recent(connected.accountId)
        if (session.value !is WatchSessionState.Connected) return reconnect()
        return when (result) {
            is ApiResult.Success -> saveNewest(connected.accountId, result.value)
            is ApiResult.Failure -> lastKnown(connected.accountId)
        }
    }

    /** The process may start a surface before the app has restored the session, so wait briefly for it to settle. */
    private suspend fun settledSession(): WatchSessionState =
        withTimeoutOrNull(SESSION_SETTLE_TIMEOUT_MILLIS) { session.first { it !is WatchSessionState.Restoring } }
            ?: WatchSessionState.NotConnected

    private suspend fun saveNewest(accountId: String, builds: List<BuildSummary>): SurfaceData {
        val now = clock()
        val newest = builds.firstOrNull()
        if (newest == null) {
            cache.clear()
            return SurfaceData(null, now, SurfaceStatus.Fresh)
        }
        cache.save(accountId, newest, now)
        return SurfaceData(newest, now, SurfaceStatus.Fresh)
    }

    private suspend fun lastKnown(accountId: String): SurfaceData {
        val cached = cache.load()?.takeIf { it.accountId == accountId }
            ?: return SurfaceData(null, null, SurfaceStatus.Stale)
        return SurfaceData(cached.build, cached.fetchedAt, SurfaceStatus.Stale)
    }

    private suspend fun notConnected(): SurfaceData {
        cache.clear()
        return SurfaceData(null, null, SurfaceStatus.NotConnected)
    }

    private suspend fun reconnect(): SurfaceData {
        cache.clear()
        return SurfaceData(null, null, SurfaceStatus.Reconnect)
    }
}
