@file:OptIn(ExperimentalTestApi::class)

package com.tomasps.virga.feature.builds.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.waitUntilDoesNotExist
import com.tomasps.virga.core.network.CloudflareApi
import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.core.navigation.BuildDetail
import com.tomasps.virga.core.navigation.WorkerBuilds
import com.tomasps.virga.core.testing.DemoFlow
import com.tomasps.virga.core.testing.FLOW_WAIT_TIMEOUT_MS
import com.tomasps.virga.core.testing.FeatureUnderTest
import com.tomasps.virga.feature.builds.BuildConfigScreen
import com.tomasps.virga.feature.builds.api.BuildsRepository
import com.tomasps.virga.feature.builds.buildsKoinModule
import com.tomasps.virga.feature.builds.registerBuildsEntries
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowToast

private const val TAG_EDGE = "tag-edge"
private const val TAG_API = "tag-api"
private const val RUNNING_BUILD = "run-1"
private const val PAGE_SIZE = 20
private const val SERVER_ERROR_TITLE = "Cloudflare is having trouble"
/** Build list items: the start button is item 0, then one per build, then the load-more footer. */
private const val LAST_PAGE_ONE_INDEX = PAGE_SIZE + 1
private const val FIRST_PAGE_TWO_INDEX = PAGE_SIZE + 1

/**
 * The state the demo cannot reach, served by a scripted API: an empty history, errors with retry, paging, a Worker
 * that is not connected, and the failures of writes. The screens and their view models are the app's own.
 */
@RunWith(RobolectricTestRunner::class)
class BuildsScriptedFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private val script = Script()
    private lateinit var flow: DemoFlow

    @Before
    fun setUp() {
        flow = DemoFlow(compose, scriptedFeature(script.api()))
    }

    @After
    fun tearDown() {
        flow.close()
    }

    @Test
    fun `a Worker with no builds shows the empty history`() {
        script.history = emptyList()

        flow.show(WorkerBuilds(DEMO_PERSONAL, DEMO_WORKER))

        flow.assertTextShown("No builds yet")
        flow.assertTextShown("Builds from the last 90 days appear here.")
    }

    @Test
    fun `a failed Worker list shows the error, and retry loads the history`() {
        script.history = listOf("p1-00")
        script.workersFail = true

        flow.show(WorkerBuilds(DEMO_PERSONAL, DEMO_WORKER))
        flow.assertTextShown(SERVER_ERROR_TITLE)
        script.workersFail = false
        flow.clickText("Retry")

        flow.assertTextShown("Untitled build p1-00")
    }

    @Test
    fun `load more appends the next page and hides the footer when a short page comes back`() {
        script.history = (0 until PAGE_SIZE).map { "p1-%02d".format(it) } + "p2-00"

        flow.show(WorkerBuilds(DEMO_PERSONAL, DEMO_WORKER))
        // The footer is the last item of a lazy list, so it is composed only once scrolled to.
        scrollBuildListTo(LAST_PAGE_ONE_INDEX)
        flow.clickText("Load more")
        compose.waitUntilDoesNotExist(hasText("Load more"), FLOW_WAIT_TIMEOUT_MS)

        scrollBuildListTo(FIRST_PAGE_TWO_INDEX)
        flow.assertTextShown("Untitled build p2-00")
    }

    @Test
    fun `starting a build with no trigger says so`() {
        script.triggers = emptyList()
        flow.show(WorkerBuilds(DEMO_PERSONAL, DEMO_WORKER))

        flow.clickText("Start a build")
        flow.assertTextShown("No build trigger is set up for this Worker. Connect a repository first.")
    }

    @Test
    fun `a failed trigger load says so in the start sheet`() {
        script.triggersFail = true
        flow.show(WorkerBuilds(DEMO_PERSONAL, DEMO_WORKER))

        flow.clickText("Start a build")
        flow.assertTextShown("Could not load build triggers. Open this again to retry.")
    }

    @Test
    fun `a start the server refuses shows a message`() {
        script.startFails = true
        flow.show(WorkerBuilds(DEMO_PERSONAL, DEMO_WORKER))

        flow.clickText("Start a build")
        flow.assertTextShown("By branch")
        flow.typeIntoField("Branch", "main")
        flow.clickText("Start build")
        compose.waitForDialogText("Start this build?")
        compose.clickInDialog("Start")

        flow.waitUntil { ShadowToast.getTextOfLatestToast() == "Could not start the build. Try again." }
    }

    @Test
    fun `a Worker that is not connected says so and offers the dashboard`() {
        script.connected = false

        flow.show(BuildConfigScreen(DEMO_PERSONAL, DEMO_WORKER))

        flow.assertTextShown("This Worker is not connected to a Git repository. Connect it in the dashboard.")
        flow.assertTextShown("Open in dashboard")
        compose.onAllNodes(hasText("Production branch")).assertCountEquals(0)
    }

    @Test
    fun `a failed previews read keeps the settings on screen and shows the error for previews`() {
        script.previewsFail = true

        flow.show(BuildConfigScreen(DEMO_PERSONAL, DEMO_WORKER))

        flow.assertTextShown(SERVER_ERROR_TITLE)
        flow.assertTextShown("npm run build")
    }

    @Test
    fun `a save the server refuses keeps the form open with the error`() {
        script.patchFails = true
        flow.show(BuildConfigScreen(DEMO_PERSONAL, DEMO_WORKER))

        flow.clickText("Edit")
        compose.waitForDialogText("Edit build settings")
        compose.typeIntoDialogField(1, "npm run build:next")
        compose.clickInDialog("Save")

        compose.waitForDialogText("Try again in a moment.")
        compose.waitForDialogText("Edit build settings")
        assertEquals(1, script.patches)
    }

    @Test
    fun `a failed settings read shows the error with a retry, and retry loads the settings`() {
        script.configFails = true
        flow.show(BuildConfigScreen(DEMO_PERSONAL, DEMO_WORKER))

        flow.waitForText("Retry")
        script.configFails = false
        flow.clickText("Retry")

        flow.assertTextShown("npm run build")
    }

    @Test
    fun `a failed cancel says so and leaves the build running`() {
        script.cancelFails = true
        flow.show(BuildDetail(DEMO_PERSONAL, RUNNING_BUILD, DEMO_WORKER))

        flow.clickText("Cancel build")
        compose.waitForDialogText("Cancel this build?")
        compose.clickInDialog("Cancel build")

        flow.waitForText("Could not cancel the build. Try again.")
        flow.assertTextShown("Cancel build")
    }

    @Test
    fun `a build that cannot be read shows the error with a retry`() {
        script.buildFails = true

        flow.show(BuildDetail(DEMO_PERSONAL, RUNNING_BUILD, DEMO_WORKER))

        flow.waitForText("Retry")
    }

    /** Scrolls the build list so the item at [index] is composed. Item 0 is the start button; builds follow it. */
    private fun scrollBuildListTo(index: Int) {
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(index)
        compose.waitForIdle()
    }

    private fun scriptedFeature(api: CloudflareApi): FeatureUnderTest = FeatureUnderTest(
        listOf(
            buildsKoinModule,
            module { single { BuildsRepository(api) } },
        ),
        ::registerBuildsEntries,
    )
}

/** Mutable answers for the scripted API. Each test sets what it needs before the screen opens. */
private class Script {
    var workersFail = false
    var history: List<String> = listOf("p1-00")
    var triggers: List<String> = listOf("trigger-edge")
    var triggersFail = false
    var startFails = false
    var startCalls = 0
    var cancelFails = false
    var buildFails = false
    var connected = true
    var previewsFail = false
    var configFails = false
    var patchFails = false
    var patches = 0

    fun api(): CloudflareApi = mockCloudflareApi {
        get("/accounts/{accountId}/workers/scripts") { _ ->
            if (workersFail) failure() else DemoReply.ok(buildJsonArray {
                add(buildJsonObject { put("id", DEMO_WORKER); put("tag", TAG_EDGE) })
                add(buildJsonObject { put("id", OTHER_WORKER); put("tag", TAG_API) })
            })
        }
        get("/accounts/{accountId}/builds/workers/{workerTag}/builds") { request ->
            val page = request.query["page"]?.toIntOrNull() ?: 1
            val perPage = request.query["per_page"]?.toIntOrNull() ?: PAGE_SIZE
            val slice = history.drop((page - 1) * perPage).take(perPage)
            DemoReply.ok(buildJsonArray { slice.forEach { add(finishedBuild(it)) } })
        }
        get("/accounts/{accountId}/builds/builds/{buildUuid}") { request ->
            when {
                buildFails -> failure()
                request.pathParams["buildUuid"] == RUNNING_BUILD -> DemoReply.ok(runningBuild())
                else -> failure(HttpStatusCode.NotFound, 10007, "build not found")
            }
        }
        get("/accounts/{accountId}/builds/builds/{buildUuid}/logs") { _ ->
            DemoReply.ok(buildJsonObject { put("lines", buildJsonArray { }) })
        }
        put("/accounts/{accountId}/builds/builds/{buildUuid}/cancel") { _ ->
            if (cancelFails) failure() else DemoReply.ok(buildJsonObject { put("build_uuid", RUNNING_BUILD) })
        }
        get("/accounts/{accountId}/builds/workers/{workerTag}/triggers") { _ ->
            if (triggersFail) failure() else DemoReply.ok(buildJsonArray {
                triggers.forEach { uuid -> add(buildJsonObject { put("trigger_uuid", uuid); put("trigger_name", "main") }) }
            })
        }
        post("/accounts/{accountId}/builds/triggers/{triggerUuid}/builds") { _ ->
            startCalls++
            if (startFails) failure() else DemoReply.ok(buildJsonObject { put("build_uuid", "started-1") })
        }
        get("/accounts/{accountId}/builds/workers/{workerTag}") { _ ->
            if (configFails) failure() else DemoReply.ok(settings())
        }
        patch("/accounts/{accountId}/builds/workers/{workerTag}") { _ ->
            patches++
            if (patchFails) failure() else DemoReply.ok(settings())
        }
        get("/accounts/{accountId}/builds/workers/{workerTag}/previews") { _ ->
            if (previewsFail) failure() else DemoReply.ok(buildJsonArray {
                add(buildJsonObject { put("preview_id", "preview-1"); put("branch", "feature/x"); put("auto_build", true) })
            })
        }
    }

    private fun settings(): JsonObject = buildJsonObject {
        if (connected) {
            putJsonObject("git_repository") {
                put("provider_type", "github")
                put("provider_account_name", "demo-owner")
                put("repo_name", DEMO_WORKER)
                put("branch", "main")
            }
            putJsonObject("production_settings") {
                put("build_command", "npm run build")
                put("deploy_command", "npx wrangler deploy")
                put("root_directory", "/")
            }
        }
        put("previews_enabled", true)
    }

    private fun finishedBuild(uuid: String): JsonObject = buildJsonObject {
        put("build_uuid", uuid)
        put("status", "stopped")
        put("build_outcome", "success")
        put("created_on", "2026-11-11T09:00:00Z")
        put("stopped_on", "2026-11-11T09:01:00Z")
    }

    private fun runningBuild(): JsonObject = buildJsonObject {
        put("build_uuid", RUNNING_BUILD)
        put("status", "running")
        put("created_on", "2026-11-11T09:00:00Z")
        put("running_on", "2026-11-11T09:00:05Z")
    }

    private fun failure(
        status: HttpStatusCode = HttpStatusCode.InternalServerError,
        code: Int = 500,
        message: String = "boom",
    ): DemoReply = DemoReply.failure(status, code, message)
}
