package com.tomasps.virga.feature.builds.watch

import com.tomasps.virga.feature.builds.api.BuildRecord

/**
 * One build the user is watching. Immutable: the engine replaces the value in the registry rather than editing it.
 * [nextPollAtMillis] and [backoffMillis] are the loop's own bookkeeping and are not persisted.
 */
data class WatchedBuild(
    val accountId: String,
    val scriptName: String,
    val buildUuid: String,
    val startedAtMillis: Long,
    val nextPollAtMillis: Long = startedAtMillis,
    val backoffMillis: Long = 0L,
    val lastRecord: BuildRecord? = null,
)

/** What survives process death: enough to resume polling a build from the same account. */
data class WatchEntry(
    val accountId: String,
    val scriptName: String,
    val buildUuid: String,
    val startedAtMillis: Long,
)

fun WatchedBuild.toEntry(): WatchEntry = WatchEntry(accountId, scriptName, buildUuid, startedAtMillis)

fun WatchEntry.toWatchedBuild(nowMillis: Long): WatchedBuild = WatchedBuild(
    accountId = accountId,
    scriptName = scriptName,
    buildUuid = buildUuid,
    startedAtMillis = startedAtMillis,
    nextPollAtMillis = nowMillis,
)
