package com.tomasps.virga.wear.summary

import android.util.Log
import com.tomasps.virga.wear.session.WatchSessionState
import com.tomasps.virga.wear.surfaces.SESSION_SETTLE_TIMEOUT_MILLIS
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant

enum class SummaryStatus { Fresh, Stale, NotConnected, Reconnect, MissingAnalyticsAccess }

/** What the summary tile renders. [data] and [fetchedAt] are both null when there is nothing to show. */
data class SummarySurface(val data: SummaryData?, val fetchedAt: Instant?, val status: SummaryStatus)

private const val TAG = "VirgaSummary"

/**
 * One refresh for the summary tile; concurrent callers take turns. A session that is not connected never reaches
 * the network. A failed fetch falls back to the cached numbers of the same account and mode, marked
 * [SummaryStatus.Stale] with the time they were fetched. Missing analytics access is a persistent condition: it is
 * shown instead of an old value, and the next refresh is the tile's normal interval, never a retry loop. If the session
 * is not the same Connected value after the call (a 401 wiped it, or the account or demo mode switched), the cache is
 * dropped and the result is [SummaryStatus.Reconnect].
 */
class SummaryRefresher(
    private val source: SummarySource,
    private val session: StateFlow<WatchSessionState>,
    private val cache: SummaryCache,
    private val warn: (String) -> Unit = { Log.e(TAG, it) },
    private val clock: () -> Instant,
) {
    private val lock = Mutex()

    suspend fun refresh(): SummarySurface = lock.withLock { refreshLocked() }

    private suspend fun refreshLocked(): SummarySurface {
        val connected = when (val state = settledSession()) {
            is WatchSessionState.Connected -> state
            WatchSessionState.Stale -> return dropped(SummaryStatus.Reconnect)
            WatchSessionState.NotConnected -> return dropped(SummaryStatus.NotConnected)
            // Restoring never settled: the session is unknown, so say "No data" and keep the cache.
            WatchSessionState.Restoring -> return SummarySurface(null, null, SummaryStatus.Stale)
        }
        val now = clock()
        val result = source.last24h(connected.accountId, now)
        if (session.value != connected) return dropped(SummaryStatus.Reconnect)
        return when (result) {
            is SummaryResult.Ok -> saved(connected, result.data, now)
            SummaryResult.MissingAnalyticsAccess -> dropped(SummaryStatus.MissingAnalyticsAccess)
            is SummaryResult.Failed -> lastKnown(connected)
        }
    }

    /** The process may start a tile before the app has restored the session, so wait briefly for it to settle. */
    private suspend fun settledSession(): WatchSessionState =
        withTimeoutOrNull(SESSION_SETTLE_TIMEOUT_MILLIS) { session.first { it !is WatchSessionState.Restoring } }
            ?: WatchSessionState.Restoring

    private suspend fun saved(session: WatchSessionState.Connected, data: SummaryData, now: Instant): SummarySurface {
        // A cache write failure must not throw away a successful fetch.
        safely("save") { cache.save(session.accountId, session.isDemo, data, now) }
        return SummarySurface(data, now, SummaryStatus.Fresh)
    }

    private suspend fun lastKnown(session: WatchSessionState.Connected): SummarySurface {
        val cached = cache.load()
        if (cached == null || cached.accountId != session.accountId || cached.isDemo != session.isDemo) {
            if (cached != null) safely("clear") { cache.clear() }
            return SummarySurface(null, null, SummaryStatus.Stale)
        }
        return SummarySurface(cached.data, cached.fetchedAt, SummaryStatus.Stale)
    }

    private suspend fun dropped(status: SummaryStatus): SummarySurface {
        safely("clear") { cache.clear() }
        return SummarySurface(null, null, status)
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

/** [SummaryRefresher.refresh] that never throws: a storage failure is logged by class name and reads as no data. */
internal suspend fun SummaryRefresher.refreshOrNull(): SummarySurface? = try {
    refresh()
} catch (error: CancellationException) {
    throw error
} catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
    Log.e(TAG, "Refresh failed: ${error::class.java.name}")
    null
}
