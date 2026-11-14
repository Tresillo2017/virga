package com.tomasps.virga.feature.builds.demo

import com.tomasps.virga.core.common.builds.BuildOutcome
import com.tomasps.virga.core.common.builds.BuildStatus
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.demo.DemoMode
import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.tomasps.virga.feature.builds.api.BuildRecord
import com.tomasps.virga.feature.builds.api.BuildTarget
import com.tomasps.virga.feature.builds.api.BuildConfigEdit
import com.tomasps.virga.feature.builds.api.BuildsRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val ACCOUNT = "acc-demo"
private const val START_MILLIS = 1_000_000L
private const val SECOND_MS = 1_000L
private val LIVE_TAG = demoTagFor(DEMO_LIVE_SCRIPT)

/** The demo's repository with a clock the test moves by hand. */
private class DemoHarness {
    var nowMillis: Long = START_MILLIS
    val repository: BuildsRepository = BuildsRepository(
        api = mockCloudflareApi {
            get("/accounts/{accountId}/workers/scripts") {
                DemoReply.ok(
                    buildJsonArray {
                        add(buildJsonObject { put("id", DEMO_LIVE_SCRIPT) })
                    },
                )
            }
            BuildsDemoRoutes { nowMillis }.contribute(this)
        },
        demoMode = DemoMode(initiallyEnabled = true),
    )

    fun advance(seconds: Long) {
        nowMillis += seconds * SECOND_MS
    }

    suspend fun read(uuid: String): BuildRecord =
        (repository.build(ACCOUNT, uuid, DEMO_LIVE_SCRIPT) as ApiResult.Success).value
}

class BuildsDemoRoutesTest {

    private lateinit var harness: DemoHarness

    @Before
    fun setUp() {
        harness = DemoHarness()
    }

    @Test
    fun `the seeded live build moves through queued, initializing, running and done in about 40 seconds`() = runBlocking {
        val queued = harness.read(DEMO_LIVE_BUILD_UUID)
        harness.advance(7)
        val initializing = harness.read(DEMO_LIVE_BUILD_UUID)
        harness.advance(6)
        val running = harness.read(DEMO_LIVE_BUILD_UUID)
        harness.advance(26)
        val done = harness.read(DEMO_LIVE_BUILD_UUID)

        assertEquals(BuildStatus.QUEUED, queued.status)
        assertEquals(BuildStatus.INITIALIZING, initializing.status)
        assertEquals(BuildStatus.RUNNING, running.status)
        assertEquals(BuildStatus.STOPPED, done.status)
        assertEquals(BuildOutcome.SUCCESS, done.outcome)
        assertTrue(done.isTerminal)
    }

    @Test
    fun `a trigger starts a fresh build for its Worker that carries the branch or commit it was given`() = runBlocking {
        val uuid = (harness.repository.startBuild(ACCOUNT, "demo-trigger-$DEMO_LIVE_SCRIPT", BuildTarget.Commit("c0ffee1"))
            as ApiResult.Success).value

        val record = harness.read(uuid)

        assertTrue(uuid != DEMO_LIVE_BUILD_UUID)
        assertEquals(BuildStatus.QUEUED, record.status)
        assertEquals("c0ffee1", record.commitHash)
    }

    @Test
    fun `cancelling a live build stops it as cancelled from that moment`() = runBlocking {
        harness.read(DEMO_LIVE_BUILD_UUID)
        harness.advance(5)
        harness.repository.cancel(ACCOUNT, DEMO_LIVE_BUILD_UUID)
        harness.advance(20)

        val record = harness.read(DEMO_LIVE_BUILD_UUID)

        assertEquals(BuildStatus.STOPPED, record.status)
        assertEquals(BuildOutcome.CANCELED, record.outcome)
    }

    @Test
    fun `log reads follow the cursor for a live build so each line arrives once`() = runBlocking {
        harness.read(DEMO_LIVE_BUILD_UUID)
        harness.advance(6)
        val first = (harness.repository.logs(ACCOUNT, DEMO_LIVE_BUILD_UUID, cursor = null) as ApiResult.Success).value
        harness.advance(4)
        val second = (harness.repository.logs(ACCOUNT, DEMO_LIVE_BUILD_UUID, cursor = first.cursor) as ApiResult.Success).value

        assertEquals(3, first.lines.size)
        assertEquals(2, second.lines.size)
        assertEquals("5", second.cursor)
    }

    @Test
    fun `the Worker's build list is paged and includes the live build as the newest`() = runBlocking {
        val page = (harness.repository.builds(ACCOUNT, LIVE_TAG, DEMO_LIVE_SCRIPT, page = 1, perPage = 2)
            as ApiResult.Success).value
        val next = (harness.repository.builds(ACCOUNT, LIVE_TAG, DEMO_LIVE_SCRIPT, page = 2, perPage = 2)
            as ApiResult.Success).value

        assertEquals(2, page.size)
        assertEquals(DEMO_LIVE_BUILD_UUID, page.first().buildUuid)
        assertEquals(2, next.size)
        assertTrue(page.none { record -> next.any { it.buildUuid == record.buildUuid } })
    }

    @Test
    fun `a historical build reads back by uuid and an unknown one is a failure`() = runBlocking {
        val known = harness.repository.build(ACCOUNT, "demo-hist-0005", DEMO_LIVE_SCRIPT)
        val unknown = harness.repository.build(ACCOUNT, "no-such-build", DEMO_LIVE_SCRIPT)

        assertEquals(BuildOutcome.FAIL, (known as ApiResult.Success).value.outcome)
        assertTrue(unknown is ApiResult.Failure)
    }

    @Test
    fun `a settings patch changes the named field and keeps its siblings`() = runBlocking {
        val before = (harness.repository.config(ACCOUNT, LIVE_TAG) as ApiResult.Success).value

        val after = (harness.repository.updateConfig(
            ACCOUNT,
            LIVE_TAG,
            BuildConfigEdit(buildCommand = "npm run build:prod"),
        ) as ApiResult.Success).value

        assertEquals("npm run build:prod", after.buildCommand)
        assertEquals(before.deployCommand, after.deployCommand)
        assertEquals(before.repoName, after.repoName)
        assertNotNull(after.productionBranch)
    }

    @Test
    fun `triggers and previews are listed for a demo Worker`() = runBlocking {
        val triggers = (harness.repository.triggers(ACCOUNT, LIVE_TAG) as ApiResult.Success).value
        val previews = (harness.repository.previews(ACCOUNT, LIVE_TAG) as ApiResult.Success).value

        assertEquals("demo-trigger-$DEMO_LIVE_SCRIPT", triggers.single().triggerUuid)
        assertEquals(1, previews.size)
    }
}
