package com.tomasps.virga.feature.builds.watch

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Two sets of watches. [active] are polled by the service and shown as watched. [paused] are saved entries from an
 * earlier process (or from a service that Android stopped). They are not polled and not shown as watched until the
 * user taps Watch again, because a service may only start from a visible tap. Both sets are saved together, so the
 * saved list never loses an entry. Saves run in order, and each one writes the latest state.
 */
class WatchRegistry(
    private val store: WatchStore,
    private val scope: CoroutineScope,
    private val clockMillis: () -> Long,
) {
    private val active = MutableStateFlow<Map<String, WatchedBuild>>(emptyMap())
    private val paused = MutableStateFlow<Map<String, WatchEntry>>(emptyMap())
    private val saveLock = Mutex()
    private val mutableStorageFailed = MutableStateFlow(false)

    /** Set when the store could not be read or written. The in-memory state still works. */
    val storageFailed: StateFlow<Boolean> = mutableStorageFailed.asStateFlow()

    /** Set after the saved list has been read. Nothing is saved before that, so the read cannot be overwritten. */
    private var restored = false

    /** UUIDs of the builds being polled. Emits only when the set changes. */
    val watchedIds: Flow<Set<String>> = active.map { it.keys.toSet() }.distinctUntilChanged()

    fun snapshot(): Map<String, WatchedBuild> = active.value

    fun isWatching(buildUuid: String): Boolean = active.value.containsKey(buildUuid)

    /** Starts a new watch, replacing a paused entry for the same build. An active watch is returned unchanged. */
    fun add(accountId: String, scriptName: String, buildUuid: String): WatchedBuild {
        active.value[buildUuid]?.let { return it }
        val now = clockMillis()
        val watch = WatchedBuild(accountId, scriptName, buildUuid, startedAtMillis = now, nextPollAtMillis = now)
        active.update { it + (buildUuid to watch) }
        paused.update { it - buildUuid }
        persist()
        return watch
    }

    /** Replaces a watch that is still active. Returns false, and changes nothing, when it was stopped meanwhile. */
    fun update(watch: WatchedBuild): Boolean {
        var replaced = false
        active.update { current ->
            if (current.containsKey(watch.buildUuid)) {
                replaced = true
                current + (watch.buildUuid to watch)
            } else {
                current
            }
        }
        return replaced
    }

    /** Stops a build, whether it is active or paused. */
    fun remove(buildUuid: String) {
        val wasActive = active.value.containsKey(buildUuid)
        val wasPaused = paused.value.containsKey(buildUuid)
        if (!wasActive && !wasPaused) return
        active.update { it - buildUuid }
        paused.update { it - buildUuid }
        persist()
    }

    /** Moves every active watch to paused. Used when Android ends the service, so the UI stops claiming they are watched. */
    fun pauseAll() {
        val drained = active.getAndUpdate { emptyMap() }
        if (drained.isEmpty()) return
        paused.update { current -> current + drained.values.associate { it.buildUuid to it.toEntry() } }
        persist()
    }

    /** Keeps only the watches of [accountId] (active and paused). Null, as after sign-out, keeps none. */
    fun retainAccount(accountId: String?) {
        val keeps: (String) -> Boolean = { it == accountId }
        val before = active.value.size + paused.value.size
        active.update { current -> current.filterValues { keeps(it.accountId) } }
        paused.update { current -> current.filterValues { keeps(it.accountId) } }
        if (active.value.size + paused.value.size != before) persist()
    }

    /**
     * Loads saved entries as paused watches. Expired entries and builds already active are skipped. Call once at
     * startup, before the first save. A failed read is recorded in [storageFailed] and nothing is saved.
     */
    suspend fun restore() {
        val saved = runCatching { store.load() }
            .onFailure { mutableStorageFailed.value = true }
            .getOrNull() ?: return
        saveLock.withLock {
            val now = clockMillis()
            val current = active.value
            val loaded = saved
                .filterNot { WatchPolicy.isExpired(it.startedAtMillis, now) || current.containsKey(it.buildUuid) }
                .associateBy { it.buildUuid }
            paused.update { it + loaded }
            restored = true
        }
        persist()
    }

    private fun persist() {
        scope.launch {
            saveLock.withLock {
                if (!restored) return@withLock
                val entries = paused.value.values + active.value.values.map { it.toEntry() }
                runCatching { store.save(entries.toList()) }
                    .onFailure { mutableStorageFailed.value = true }
                    .onSuccess { mutableStorageFailed.value = false }
            }
        }
    }
}
