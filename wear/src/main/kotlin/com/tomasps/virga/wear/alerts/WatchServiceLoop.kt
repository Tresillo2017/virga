package com.tomasps.virga.wear.alerts

import com.tomasps.virga.feature.builds.watch.WatchPolicy
import com.tomasps.virga.feature.builds.watch.WatchRegistry
import com.tomasps.virga.feature.builds.watch.WatchServiceStopPolicy

/**
 * The stop decision of [WearWatchService], with no Android in it. The start id is read FIRST, then the registry is
 * checked, then Android is asked to stop for that id. A watch added after the id was read is caught by Android
 * (it refuses a stop naming an older start). A watch added before the read is caught by the registry check. Either
 * way a watch tapped while the last one was finishing is never left active with nothing polling it.
 */
class ServiceStopGate(
    private val registry: WatchRegistry,
    private val latestStartId: () -> Int,
    private val stopSelfResult: (Int) -> Boolean,
) {
    /** True when the service stopped. False means keep running: something is watched or a newer start is pending. */
    fun tryStop(): Boolean {
        val startId = latestStartId()
        if (!WatchServiceStopPolicy.mayStop(registry.snapshot().size)) return false
        return stopSelfResult(startId)
    }
}

/** Runs the watch loop until the gate lets the service stop. A refused stop retries after the shortest delay. */
class WatchServiceLoop(
    private val runner: WearWatchRunner,
    private val gate: ServiceStopGate,
    private val sleep: suspend (Long) -> Unit,
) {
    suspend fun run() {
        while (true) {
            runner.run()
            if (gate.tryStop()) return
            sleep(WatchPolicy.MIN_LOOP_DELAY_MS)
        }
    }
}

/**
 * What the service does when its coroutines throw something unexpected: log the class name only (a message could
 * carry credential data), pause the watches so the screen stops claiming they are watched, and stop cleanly.
 */
class WatchCrashGuard(
    private val registry: WatchRegistry,
    private val log: (String) -> Unit,
    private val stop: () -> Unit,
) {
    fun onFailure(error: Throwable) {
        log("Watch service failed: ${error.javaClass.name}")
        registry.pauseAll()
        stop()
    }
}
