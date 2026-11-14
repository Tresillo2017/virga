package com.tomasps.virga.wear.alerts

import com.tomasps.virga.feature.builds.watch.WatchRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * What the detail screen needs to watch a build. Called only from a Watch tap while the app is visible, because a
 * foreground service may only start from a visible tap.
 *
 * Saved watches are NOT restored after process death: the registry reloads them as paused, and they stay paused until
 * the wearer taps Watch again, because a foreground service cannot start from the background. This mirrors the phone.
 * A watch started here is polled by the watch alone; the phone never learns about it.
 */
interface WatchStarter {
    /** Route keys (`script/uuid`) of the builds being polled, emitting on every change. */
    val watchedKeys: Flow<Set<String>>

    /** Starts watching, and the service if it is not running. A build that is already watched is left alone. */
    fun start(accountId: String, scriptName: String, buildUuid: String)

    fun stop(scriptName: String, buildUuid: String)
}

/** [WatchStarter] over the registry. [launchService] starts the foreground service; it is called once per new watch. */
class RegistryWatchStarter(
    private val registry: WatchRegistry,
    private val launchService: () -> Unit,
) : WatchStarter {

    override val watchedKeys: Flow<Set<String>> = registry.watchedIds.map {
        registry.snapshot().values.map { "${it.scriptName}/${it.buildUuid}" }.toSet()
    }

    override fun start(accountId: String, scriptName: String, buildUuid: String) {
        if (registry.isWatching(buildUuid)) return
        registry.add(accountId, scriptName, buildUuid)
        launchService()
    }

    override fun stop(scriptName: String, buildUuid: String) {
        if (registry.snapshot()[buildUuid]?.scriptName == scriptName) registry.remove(buildUuid)
    }
}
