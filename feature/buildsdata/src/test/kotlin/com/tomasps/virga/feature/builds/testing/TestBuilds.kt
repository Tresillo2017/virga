package com.tomasps.virga.feature.builds.testing

import com.tomasps.virga.core.common.builds.BuildOutcome
import com.tomasps.virga.core.common.builds.BuildStatus
import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.feature.builds.api.BuildRecord
import com.tomasps.virga.feature.builds.watch.BuildFetcher
import com.tomasps.virga.feature.builds.watch.WatchEntry
import com.tomasps.virga.feature.builds.watch.WatchStore
import java.time.Instant

const val TEST_ACCOUNT = "acc-test"
const val TEST_SCRIPT = "api-worker"
const val TEST_BUILD = "build-1"
val T0: Instant = Instant.parse("2026-11-11T10:00:00Z")

/** A record in the given status. Terminal states get a stop time and an outcome. */
fun record(
    uuid: String = TEST_BUILD,
    status: BuildStatus = BuildStatus.RUNNING,
    outcome: BuildOutcome? = null,
    branch: String? = "main",
    created: Instant = T0,
) = BuildRecord(
    buildUuid = uuid,
    scriptName = TEST_SCRIPT,
    status = status,
    outcome = outcome,
    branch = branch,
    commitHash = "abc1234",
    commitMessage = "Fix thing",
    author = "dev@example.test",
    createdOn = created,
    initializingOn = created.plusSeconds(1),
    runningOn = created.plusSeconds(2),
    stoppedOn = if (status == BuildStatus.STOPPED) created.plusSeconds(30) else null,
)

/** Replies from a per-build queue. The last reply repeats once the queue is down to one entry. */
class ScriptedFetcher(replies: Map<String, List<ApiResult<BuildRecord>>>) : BuildFetcher {
    private val queues = replies.mapValues { it.value.toMutableList() }.toMutableMap()
    val calls = mutableListOf<String>()

    override suspend fun fetch(accountId: String, scriptName: String, buildUuid: String): ApiResult<BuildRecord> {
        calls += buildUuid
        val queue = queues[buildUuid] ?: error("no script for $buildUuid")
        return if (queue.size > 1) queue.removeAt(0) else queue.first()
    }

    fun callsFor(buildUuid: String): Int = calls.count { it == buildUuid }
}

fun ok(record: BuildRecord): ApiResult<BuildRecord> = ApiResult.Success(record)
fun rateLimited(retryAfterMillis: Long): ApiResult<BuildRecord> =
    ApiResult.Failure(ApiError.RateLimited(retryAfterMillis))

class FakeWatchStore(initial: List<WatchEntry> = emptyList()) : WatchStore {
    var saved: List<WatchEntry> = initial
        private set
    var failLoad = false

    override suspend fun load(): List<WatchEntry> {
        if (failLoad) error("unreadable")
        return saved
    }

    override suspend fun save(entries: List<WatchEntry>) {
        saved = entries
    }
}

/** Asserts the value's runtime type and returns it cast, so tests can read its fields. */
inline fun <reified T> assertIs(value: Any?): T {
    org.junit.Assert.assertTrue("expected ${T::class.simpleName} but was $value", value is T)
    return value as T
}
