package com.tomasps.virga.feature.builds.watch

import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.feature.builds.api.BuildDto
import com.tomasps.virga.feature.builds.api.BuildRecord
import com.tomasps.virga.feature.builds.api.toRecord
import com.tomasps.virga.feature.builds.testing.FakeWatchStore
import com.tomasps.virga.feature.builds.testing.ScriptedFetcher
import com.tomasps.virga.feature.builds.testing.TEST_ACCOUNT
import com.tomasps.virga.feature.builds.testing.TEST_BUILD
import com.tomasps.virga.feature.builds.testing.TEST_SCRIPT
import com.tomasps.virga.feature.builds.testing.ok
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the watch loop does with one wire reply: the event it reports, and whether the watch stays registered. */
private sealed interface Expected {
    data object Progress : Expected
    data class Finished(val kind: FinalKind) : Expected
}

private data class Reply(val status: String?, val outcome: String?, val expected: Expected)

/** Wire spellings as the REST API and the events schema send them, mapped the same way the repository maps them. */
private val replies = listOf(
    Reply("queued", null, Expected.Progress),
    Reply("initializing", null, Expected.Progress),
    Reply("running", null, Expected.Progress),
    Reply("RUNNING", null, Expected.Progress),
    Reply("mystery", null, Expected.Progress),
    Reply("stopped", null, Expected.Finished(FinalKind.FINISHED)),
    Reply("stopped", "success", Expected.Finished(FinalKind.SUCCEEDED)),
    Reply("stopped", "fail", Expected.Finished(FinalKind.FAILED)),
    Reply("stopped", "failure", Expected.Finished(FinalKind.FAILED)),
    Reply("stopped", "failed", Expected.Finished(FinalKind.FAILED)),
    Reply("stopped", "canceled", Expected.Finished(FinalKind.CANCELED)),
    Reply("stopped", "cancelled", Expected.Finished(FinalKind.CANCELED)),
    Reply("stopped", "skipped", Expected.Finished(FinalKind.SKIPPED)),
    Reply("stopped", "terminated", Expected.Finished(FinalKind.FINISHED)),
    Reply("running", "success", Expected.Finished(FinalKind.SUCCEEDED)),
    Reply("mystery", "failure", Expected.Finished(FinalKind.FAILED)),
)

@OptIn(ExperimentalCoroutinesApi::class)
class WatchStateMachineTest {

    private fun dto(status: String?, outcome: String?) = BuildDto(
        buildUuid = TEST_BUILD,
        status = status,
        buildOutcome = outcome,
        createdOn = "2026-11-11T10:00:00Z",
        stoppedOn = if (status == "stopped") "2026-11-11T10:01:00Z" else null,
    )

    private fun recordFor(reply: Reply): BuildRecord =
        dto(reply.status, reply.outcome).toRecord(TEST_SCRIPT) ?: error("fixture has no record")

    @Test
    fun `every wire status and outcome spelling reports the event its state calls for`() = runTest {
        for (reply in replies) {
            val events = mutableListOf<WatchEvent>()
            val registry = WatchRegistry(FakeWatchStore(), backgroundScope, clockMillis = { 0L })
            registry.add(TEST_ACCOUNT, TEST_SCRIPT, TEST_BUILD)
            val fetcher = ScriptedFetcher(mapOf(TEST_BUILD to listOf(ok(recordFor(reply)))))

            WatchEngine(registry, fetcher, budget = { null }, onEvent = { events += it }).tick(0L)

            val label = "status=${reply.status} outcome=${reply.outcome}"
            when (val expected = reply.expected) {
                Expected.Progress -> {
                    assertTrue(label, events.single() is WatchEvent.Progress)
                    assertTrue(label, registry.isWatching(TEST_BUILD))
                }
                is Expected.Finished -> {
                    val finished = events.single() as? WatchEvent.Finished ?: error("$label: expected Finished")
                    assertEquals(label, expected.kind, finalKindOf(finished.record))
                    assertTrue(label, !registry.isWatching(TEST_BUILD))
                }
            }
        }
    }

    @Test
    fun `a watch on a build that already finished ends at its first read with its result and nothing left to poll`() = runTest {
        val events = mutableListOf<WatchEvent>()
        val registry = WatchRegistry(FakeWatchStore(), backgroundScope, clockMillis = { 0L })
        registry.add(TEST_ACCOUNT, TEST_SCRIPT, TEST_BUILD)
        val done = dto("stopped", "success").toRecord(TEST_SCRIPT) ?: error("fixture has no record")
        val engine = WatchEngine(
            registry,
            ScriptedFetcher(mapOf(TEST_BUILD to listOf<ApiResult<BuildRecord>>(ok(done)))),
            budget = { null },
            onEvent = { events += it },
        )

        val nextDelay = engine.tick(0L)

        assertEquals(1, events.size)
        assertEquals(FinalKind.SUCCEEDED, finalKindOf((events.single() as WatchEvent.Finished).record))
        assertTrue(registry.snapshot().isEmpty())
        assertNull("nothing is due, so the service has no reason to keep polling", nextDelay)
    }
}
