package com.tomasps.virga.wear.alerts

import com.tomasps.virga.core.network.RateBudget
import com.tomasps.virga.feature.builds.watch.BuildFetcher
import com.tomasps.virga.feature.builds.watch.WatchEngine
import com.tomasps.virga.feature.builds.watch.WatchEvent
import com.tomasps.virga.feature.builds.watch.WatchPolicy
import com.tomasps.virga.feature.builds.watch.WatchRegistry
import com.tomasps.virga.wear.session.WatchSessionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

/** Why [WearWatchRunner.run] returned. Either way the service has nothing left to do and stops. */
enum class WatchStop { RegistryEmpty, SessionEnded }

/**
 * The service's loop with no Android in it, so plain JVM tests drive it with a fake clock and sleep. Before every poll
 * it keeps only the watches of the connected account (none when the session is Stale, NotConnected or still
 * Restoring), so a stale credential is never used to poll. It returns as soon as nothing is left to watch.
 */
class WearWatchRunner(
    private val registry: WatchRegistry,
    private val session: StateFlow<WatchSessionState>,
    fetcher: BuildFetcher,
    budget: () -> RateBudget?,
    onEvent: suspend (WatchEvent) -> Unit,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    private val engine = WatchEngine(registry, fetcher, budget, onEvent)

    suspend fun run(): WatchStop {
        while (true) {
            val accountId = (session.value as? WatchSessionState.Connected)?.accountId
            registry.retainAccount(accountId)
            if (accountId == null) return WatchStop.SessionEnded
            if (registry.snapshot().isEmpty()) return WatchStop.RegistryEmpty
            val nextDueInMillis = engine.tick(clockMillis())
            // Return before sleeping: the last watch finishing must not hold the service up for a loop delay.
            if (registry.snapshot().isEmpty()) return WatchStop.RegistryEmpty
            sleep(WatchPolicy.loopDelayMillis(nextDueInMillis))
        }
    }
}
