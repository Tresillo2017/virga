package com.tomasps.virga.feature.builds.demo

import com.tomasps.virga.core.common.builds.BuildOutcome
import com.tomasps.virga.core.common.builds.BuildStatus
import java.time.Instant

/**
 * The demo Worker whose seeded build moves through its stages. It is one of the scripts the Workers demo lists.
 * The canned scripts list has no tags, so [demoTagFor] supplies one for every name.
 */
internal const val DEMO_LIVE_SCRIPT = "edge-api"

/** Prefix of every demo tag. The tag carries the script name, so the routes need no lookup table. */
private const val DEMO_TAG_PREFIX = "demo-tag-"

/** Demo tag for a Worker name. Only the demo routes read it back, through [scriptNameForDemoTag]. */
internal fun demoTagFor(scriptName: String): String = DEMO_TAG_PREFIX + scriptName

/** The script name a demo tag stands for, or null when the tag is not a demo tag. */
internal fun scriptNameForDemoTag(tag: String): String? =
    tag.takeIf { it.startsWith(DEMO_TAG_PREFIX) }?.removePrefix(DEMO_TAG_PREFIX)?.takeIf { it.isNotEmpty() }

/** The seeded live build's UUID. Builds triggered from the demo get their own UUIDs. */
internal const val DEMO_LIVE_BUILD_UUID = "demo-live-0001-4b2a-9c1d-000000000001"

/** Offsets from the live build's first read, in milliseconds. About 40 seconds from start to finish. */
internal const val LIVE_INITIALIZING_AT_MS = 6_000L
internal const val LIVE_RUNNING_AT_MS = 12_000L
internal const val LIVE_FINISHED_AT_MS = 38_000L
private const val LIVE_LOG_INTERVAL_MS = 2_000L
private const val LIVE_LOG_LINES_MAX = 20

/** A build as the demo serves it, with timestamps resolved to instants. */
internal data class DemoBuild(
    val uuid: String,
    val scriptName: String,
    val status: BuildStatus,
    val outcome: BuildOutcome?,
    val branch: String,
    val commitHash: String,
    val commitMessage: String,
    val author: String,
    val createdOn: Instant,
    val initializingOn: Instant?,
    val runningOn: Instant?,
    val stoppedOn: Instant?,
)

/**
 * The live demo build's state at [nowMillis], given when it was first read. Pure, so the timing is testable.
 * A cancel at [cancelledAtMillis] stops it there with outcome canceled.
 */
internal fun liveDemoBuild(
    uuid: String,
    anchorMillis: Long,
    nowMillis: Long,
    cancelledAtMillis: Long?,
    scriptName: String,
): DemoBuild {
    val created = Instant.ofEpochMilli(anchorMillis)
    val elapsed = nowMillis - anchorMillis
    val cancelledAt = cancelledAtMillis?.takeIf { it <= nowMillis }
    val base = DemoBuild(
        uuid = uuid,
        scriptName = scriptName,
        status = BuildStatus.QUEUED,
        outcome = null,
        branch = "main",
        commitHash = "9f3c2ab",
        commitMessage = "Tune cache headers for assets",
        author = "dev@example.test",
        createdOn = created,
        initializingOn = null,
        runningOn = null,
        stoppedOn = null,
    )
    return when {
        cancelledAt != null -> base.copy(
            status = BuildStatus.STOPPED,
            outcome = BuildOutcome.CANCELED,
            initializingOn = created.plusMillis(LIVE_INITIALIZING_AT_MS).takeIf { cancelledAt > anchorMillis + LIVE_INITIALIZING_AT_MS },
            stoppedOn = Instant.ofEpochMilli(cancelledAt),
        )
        elapsed < LIVE_INITIALIZING_AT_MS -> base
        elapsed < LIVE_RUNNING_AT_MS -> base.copy(
            status = BuildStatus.INITIALIZING,
            initializingOn = created.plusMillis(LIVE_INITIALIZING_AT_MS),
        )
        elapsed < LIVE_FINISHED_AT_MS -> base.copy(
            status = BuildStatus.RUNNING,
            initializingOn = created.plusMillis(LIVE_INITIALIZING_AT_MS),
            runningOn = created.plusMillis(LIVE_RUNNING_AT_MS),
        )
        else -> base.copy(
            status = BuildStatus.STOPPED,
            outcome = BuildOutcome.SUCCESS,
            initializingOn = created.plusMillis(LIVE_INITIALIZING_AT_MS),
            runningOn = created.plusMillis(LIVE_RUNNING_AT_MS),
            stoppedOn = created.plusMillis(LIVE_FINISHED_AT_MS),
        )
    }
}

/** Log lines the live build has produced by [nowMillis]: one every couple of seconds once it is running. */
internal fun liveDemoLogLines(anchorMillis: Long, nowMillis: Long): List<String> {
    val elapsed = (nowMillis - anchorMillis).coerceAtLeast(0L)
    val count = (elapsed / LIVE_LOG_INTERVAL_MS).toInt().coerceAtMost(LIVE_LOG_LINES_MAX)
    return (1..count).map { index -> LIVE_LOG_MESSAGES[(index - 1) % LIVE_LOG_MESSAGES.size] }
}

private val LIVE_LOG_MESSAGES = listOf(
    "Cloning repository",
    "Installing dependencies",
    "Running build command",
    "Bundling worker",
    "Uploading version",
    "Deploying to production",
)

/** Finished builds for the history list. Newest first. */
internal fun historicalDemoBuilds(scriptName: String, now: Instant): List<DemoBuild> = listOf(
    finished("demo-hist-0006", scriptName, BuildOutcome.SUCCESS, "main", "Fix login redirect", "9d1e0c4", now.minusSeconds(3_600), 95),
    finished("demo-hist-0005", scriptName, BuildOutcome.FAIL, "main", "Bump the parser dependency", "3ab7f21", now.minusSeconds(7_200), 61),
    finished("demo-hist-0004", scriptName, BuildOutcome.SKIPPED, "feature/search", "WIP search index", "c08e5d9", now.minusSeconds(9_000), 0),
    finished("demo-hist-0003", scriptName, BuildOutcome.CANCELED, "feature/search", "Try a new index layout", "a6f2b13", now.minusSeconds(86_400), 40),
    finished("demo-hist-0002", scriptName, BuildOutcome.SUCCESS, "main", "Add rate limit headers", "e4b9a70", now.minusSeconds(172_800), 88),
    finished("demo-hist-0001", scriptName, BuildOutcome.SUCCESS, "main", "Initial deploy", "0a1b2c3", now.minusSeconds(604_800), 73),
)

private fun finished(
    uuid: String,
    scriptName: String,
    outcome: BuildOutcome,
    branch: String,
    message: String,
    commit: String,
    createdAgoBase: Instant,
    durationSeconds: Long,
): DemoBuild {
    val created = createdAgoBase
    val stopped = created.plusSeconds(durationSeconds)
    return DemoBuild(
        uuid = uuid,
        scriptName = scriptName,
        status = BuildStatus.STOPPED,
        outcome = outcome,
        branch = branch,
        commitHash = commit,
        commitMessage = message,
        author = "dev@example.test",
        createdOn = created,
        initializingOn = created.plusSeconds(1),
        runningOn = created.plusSeconds(2),
        stoppedOn = if (durationSeconds == 0L) null else stopped,
    )
}

private fun finished(
    uuid: String,
    scriptName: String,
    outcome: BuildOutcome,
    branch: String,
    message: String,
    commit: String,
    createdAt: Long,
    durationSeconds: Long,
): DemoBuild = finished(uuid, scriptName, outcome, branch, message, commit, Instant.ofEpochSecond(createdAt), durationSeconds)
