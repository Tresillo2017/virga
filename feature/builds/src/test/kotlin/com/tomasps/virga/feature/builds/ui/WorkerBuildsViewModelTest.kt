package com.tomasps.virga.feature.builds.ui

import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.core.ui.components.LoadMoreState
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.feature.builds.api.BuildTarget
import com.tomasps.virga.feature.builds.api.BuildsRepository
import com.tomasps.virga.feature.builds.testing.MainDispatcherRule
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val ACCOUNT = "acc-1"
private const val SCRIPT = "api-worker"
private const val TAG = "tag-1"
private const val WAIT_MS = 5_000L

/** A page of [count] builds whose UUIDs carry the page number, so the test can tell pages apart. */
private fun page(pageNumber: Int, count: Int): JsonArray = buildJsonArray {
    repeat(count) { index ->
        add(buildJsonObject {
            put("build_uuid", "p$pageNumber-$index")
            put("status", "stopped")
            put("build_outcome", "success")
            put("created_on", "2026-11-11T10:00:00Z")
            put("stopped_on", "2026-11-11T10:01:00Z")
        })
    }
}

class WorkerBuildsViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    /** Start-build calls seen by the fake API, and whether the trigger list should fail. */
    private class Calls {
        var startCalls = 0
        var startBodies = mutableListOf<String>()
        var triggerLoads = 0
        var triggersFail = false
    }

    private fun viewModel(
        pageFailsOn: Int? = null,
        pageSize: Int = 2,
        firstPageOverride: JsonArray? = null,
        calls: Calls = Calls(),
    ): WorkerBuildsViewModel {
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/workers/scripts") {
                DemoReply.ok(buildJsonArray { add(buildJsonObject { put("id", SCRIPT); put("tag", TAG) }) })
            }
            get("/accounts/{accountId}/builds/workers/{workerTag}/builds") { request ->
                val pageNumber = request.query["page"]?.toInt() ?: 1
                when {
                    pageNumber == pageFailsOn -> DemoReply.failure(HttpStatusCode.InternalServerError, 500, "boom")
                    pageNumber == 1 -> DemoReply.ok(firstPageOverride ?: page(1, pageSize))
                    pageNumber == 2 -> DemoReply.ok(page(2, 1))
                    else -> DemoReply.ok(JsonArray(emptyList()))
                }
            }
            get("/accounts/{accountId}/builds/workers/{workerTag}/triggers") {
                calls.triggerLoads++
                if (calls.triggersFail) {
                    DemoReply.failure(HttpStatusCode.InternalServerError, 500, "boom")
                } else {
                    DemoReply.ok(buildJsonArray { add(buildJsonObject { put("trigger_uuid", "t-1"); put("trigger_name", "main") }) })
                }
            }
            post("/accounts/{accountId}/builds/triggers/{triggerUuid}/builds") { request ->
                calls.startCalls++
                calls.startBodies += request.bodyText.orEmpty()
                DemoReply.ok(buildJsonObject { put("build_uuid", "b-9") })
            }
        }
        return mainDispatcher.keep(WorkerBuildsViewModel::class.java) { WorkerBuildsViewModel(BuildsRepository(api), ACCOUNT, SCRIPT, pageSize = pageSize) }
    }

    private suspend fun WorkerBuildsViewModel.awaitTriggers(): WorkerBuildsUiState =
        withTimeout(WAIT_MS) { state.first { it.triggersLoaded || it.startSheetError != null } }

    private suspend fun WorkerBuildsViewModel.awaitContent(size: Int): WorkerBuildsUiState =
        withTimeout(WAIT_MS) {
            state.first { current -> (current.builds as? UiState.Content)?.data?.size == size && !current.isRefreshing }
        }

    @Test
    fun `the first page loads and a full page leaves more to load`() = mainDispatcher.runVmTest {
        val vm = viewModel()

        val loaded = vm.awaitContent(size = 2)

        assertEquals(LoadMoreState.AVAILABLE, loaded.loadMoreState)
        assertEquals(1, loaded.page)
    }

    @Test
    fun `load more appends the next page and then hides the footer when a page comes back short`() = mainDispatcher.runVmTest {
        val vm = viewModel()
        vm.awaitContent(size = 2)

        vm.loadMore()
        val appended = vm.awaitContent(size = 3)

        assertEquals(listOf("p1-0", "p1-1", "p2-0"), (appended.builds as UiState.Content).data.map { it.buildUuid })
        assertEquals(LoadMoreState.HIDDEN, appended.loadMoreState)
    }

    @Test
    fun `a failed load more keeps the rows already shown and marks the footer failed`() = mainDispatcher.runVmTest {
        val vm = viewModel(pageFailsOn = 2)
        vm.awaitContent(size = 2)

        vm.loadMore()
        val failed = withTimeout(WAIT_MS) { vm.state.first { it.loadMoreFailed } }

        assertEquals(LoadMoreState.FAILED, failed.loadMoreState)
        assertEquals(2, (failed.builds as UiState.Content).data.size)
        assertTrue(!failed.isLoadingMore)
    }

    @Test
    fun `a full page with one malformed item still leaves more to load`() = mainDispatcher.runVmTest {
        val malformedPage = buildJsonArray {
            add(buildJsonObject { put("build_uuid", "p1-0"); put("status", "stopped"); put("created_on", "2026-11-11T10:00:00Z") })
            add(buildJsonObject { put("build_uuid", "p1-bad") })
        }
        val vm = viewModel(pageSize = 2, firstPageOverride = malformedPage)

        val loaded = vm.awaitContent(size = 1)

        assertEquals(LoadMoreState.AVAILABLE, loaded.loadMoreState)
    }

    @Test
    fun `a start is confirmed before anything is sent, then sent once from the first trigger`() = mainDispatcher.runVmTest {
        val calls = Calls()
        val vm = viewModel(calls = calls)
        vm.openStartSheet()
        vm.awaitTriggers()

        vm.requestStart(BuildTarget.Branch("main"))
        assertEquals(BuildTarget.Branch("main"), vm.state.value.pendingStart)
        assertEquals(0, calls.startCalls)

        vm.dismissStart()
        assertEquals(0, calls.startCalls)

        vm.requestStart(BuildTarget.Commit("abc1234"))
        vm.confirmStart()
        withTimeout(WAIT_MS) { vm.state.first { !it.isStarting && it.message != null } }

        assertEquals(1, calls.startCalls)
        assertTrue(calls.startBodies.single().contains("abc1234"))
        assertEquals(null, vm.state.value.pendingStart)
    }

    @Test
    fun `a failed trigger load shows the error and is asked again on the next open`() = mainDispatcher.runVmTest {
        val calls = Calls().apply { triggersFail = true }
        val vm = viewModel(calls = calls)

        vm.openStartSheet()
        val failed = vm.awaitTriggers()
        assertTrue(failed.startSheetError != null)
        assertTrue(!failed.triggersLoaded)

        calls.triggersFail = false
        vm.closeStartSheet()
        vm.openStartSheet()
        val loaded = withTimeout(WAIT_MS) { vm.state.first { it.triggersLoaded } }

        assertEquals(2, calls.triggerLoads)
        assertEquals(1, loaded.triggers.size)
    }

    @Test
    fun `a start target is a trimmed branch or commit and blank input is refused`() {
        assertEquals(BuildTarget.Branch("main"), buildTargetFor(byCommit = false, value = "  main "))
        assertEquals(BuildTarget.Commit("abc1234"), buildTargetFor(byCommit = true, value = "abc1234"))
        assertEquals(null, buildTargetFor(byCommit = true, value = "   "))
    }
}
