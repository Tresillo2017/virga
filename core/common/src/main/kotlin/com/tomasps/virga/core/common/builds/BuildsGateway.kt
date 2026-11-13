package com.tomasps.virga.core.common.builds

import com.tomasps.virga.core.common.result.ApiResult
import kotlinx.coroutines.flow.Flow

/**
 * Cross-feature access to Workers Builds. The Workers and Home features read builds and start watching them
 * through this interface; :feature:builds implements it and binds it in its Koin module. Features never
 * depend on :feature:builds directly.
 */
interface BuildsGateway {
    /** Most recent builds of one Worker, newest first, at most [limit]. */
    suspend fun recentBuilds(accountId: String, scriptName: String, limit: Int): ApiResult<List<BuildSummary>>

    /** Builds that are queued, initializing or running across the whole account. */
    suspend fun activeBuilds(accountId: String): ApiResult<List<BuildSummary>>

    /** Build UUIDs currently pinned by the watch service. Emits again whenever the set changes. */
    fun watchedBuildIds(): Flow<Set<String>>

    /** Starts the watch service for [build] (the user's Watch tap, made while the app is visible). */
    fun startWatching(accountId: String, build: BuildSummary)

    /** Stops watching [buildUuid]. Does nothing if it is not watched. */
    fun stopWatching(buildUuid: String)
}
