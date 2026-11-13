package com.tomasps.virga.feature.builds.watch

import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.RateBudget
import com.tomasps.virga.feature.builds.api.BuildRecord

/** Reads one build. The gateway passes the repository; tests pass a scripted fake. */
fun interface BuildFetcher {
    suspend fun fetch(accountId: String, scriptName: String, buildUuid: String): ApiResult<BuildRecord>
}

/** What the watch loop reports. The Android shell turns these into notifications. */
sealed interface WatchEvent {
    val build: WatchedBuild

    data class Progress(override val build: WatchedBuild, val record: BuildRecord) : WatchEvent
    data class Finished(override val build: WatchedBuild, val record: BuildRecord) : WatchEvent
    data class Expired(override val build: WatchedBuild) : WatchEvent
    data class Failed(override val build: WatchedBuild, val error: ApiError) : WatchEvent
}

/**
 * One poll pass over the registry. [tick] reads every watch that is due and reports what changed. It holds no
 * timer of its own: the service calls it, sleeps for the returned delay, and calls it again. Plain Kotlin, so
 * the fake clock and fake fetcher in the tests drive it directly.
 */
class WatchEngine(
    private val registry: WatchRegistry,
    private val fetcher: BuildFetcher,
    private val budget: () -> RateBudget?,
    private val onEvent: suspend (WatchEvent) -> Unit,
) {
    /** Polls each due watch once. Returns the milliseconds until the next watch is due, or null when none is left. */
    suspend fun tick(nowMillis: Long): Long? {
        registry.snapshot().values
            .sortedBy { it.startedAtMillis }
            .forEach { watch -> processWatch(watch, nowMillis) }
        return registry.snapshot().values
            .minOfOrNull { it.nextPollAtMillis }
            ?.let { (it - nowMillis).coerceAtLeast(0L) }
    }

    private suspend fun processWatch(watch: WatchedBuild, nowMillis: Long) {
        if (WatchPolicy.isExpired(watch.startedAtMillis, nowMillis)) {
            registry.remove(watch.buildUuid)
            onEvent(WatchEvent.Expired(watch))
            return
        }
        if (watch.nextPollAtMillis > nowMillis) return
        when (val result = fetcher.fetch(watch.accountId, watch.scriptName, watch.buildUuid)) {
            is ApiResult.Success -> onSuccess(watch, result.value, nowMillis)
            is ApiResult.Failure -> onFailure(watch, result.error, nowMillis)
        }
    }

    private suspend fun onSuccess(watch: WatchedBuild, record: BuildRecord, nowMillis: Long) {
        if (record.isTerminal) {
            registry.remove(watch.buildUuid)
            onEvent(WatchEvent.Finished(watch.copy(lastRecord = record), record))
            return
        }
        val next = watch.copy(
            lastRecord = record,
            nextPollAtMillis = nowMillis + WatchPolicy.intervalMillis(budget()),
            backoffMillis = 0L,
        )
        if (registry.update(next)) onEvent(WatchEvent.Progress(next, record))
    }

    private suspend fun onFailure(watch: WatchedBuild, error: ApiError, nowMillis: Long) {
        when (error) {
            is ApiError.RateLimited ->
                delayWatch(watch, nowMillis, WatchPolicy.backoffAfterError(watch.backoffMillis, error.retryAfterMillis))
            is ApiError.NotFound, is ApiError.Unauthorized, is ApiError.Forbidden -> {
                registry.remove(watch.buildUuid)
                onEvent(WatchEvent.Failed(watch, error))
            }
            else -> delayWatch(watch, nowMillis, WatchPolicy.backoffAfterError(watch.backoffMillis, null))
        }
    }

    private fun delayWatch(watch: WatchedBuild, nowMillis: Long, backoffMillis: Long) {
        registry.update(watch.copy(nextPollAtMillis = nowMillis + backoffMillis, backoffMillis = backoffMillis))
    }
}
