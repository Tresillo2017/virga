package com.tomasps.virga.feature.builds.ui

import com.tomasps.virga.core.common.builds.BuildSummary
import com.tomasps.virga.core.common.builds.BuildsGateway
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.demo.DemoReply
import io.ktor.http.HttpStatusCode
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.feature.builds.api.BuildsRepository
import com.tomasps.virga.feature.builds.testing.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val ACCOUNT = "acc-1"
private const val BUILD = "b-1"
private const val SCRIPT = "api-worker"
private const val WAIT_MS = 5_000L
private const val POLL_MS = 1L

private fun json(text: String): JsonElement = Json.parseToJsonElement(text)

private fun buildJson(status: String, outcome: String?): String {
    val outcomeField = outcome?.let { ",\"build_outcome\":\"$it\"" }.orEmpty()
    val stopped = if (status == "stopped") ",\"stopped_on\":\"2026-11-11T10:01:00Z\"" else ""
    return "{\"build_uuid\":\"$BUILD\",\"status\":\"$status\"$outcomeField," +
        "\"created_on\":\"2026-11-11T10:00:00Z\",\"running_on\":\"2026-11-11T10:00:05Z\"$stopped}"
}

/** Gateway fake: records watch calls and exposes the watched set as a flow. */
private class FakeGateway : BuildsGateway {
    val watched = MutableStateFlow<Set<String>>(emptySet())
    val started = mutableListOf<String>()

    override suspend fun recentBuilds(accountId: String, scriptName: String, limit: Int) = ApiResult.Success(emptyList<BuildSummary>())
    override suspend fun activeBuilds(accountId: String) = ApiResult.Success(emptyList<BuildSummary>())
    override fun watchedBuildIds(): Flow<Set<String>> = watched
    override fun startWatching(accountId: String, build: BuildSummary) {
        started += build.buildUuid
        watched.value = watched.value + build.buildUuid
    }
    override fun stopWatching(buildUuid: String) {
        watched.value = watched.value - buildUuid
    }
}

class BuildDetailViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    /**
     * Serves the build as [statuses] in order, repeating the last one. Build reads whose zero-based index is in
     * [failingReads] get [failure] instead. The log is served by each test.
     */
    private class Script(
        private val statuses: List<Pair<String, String?>>,
        private val failingReads: Set<Int> = emptySet(),
        private val failure: DemoReply = DemoReply.failure(HttpStatusCode.InternalServerError, 1000, "unavailable"),
    ) {
        var buildReads = 0
        var cancelCalls = 0
        val logQueries = mutableListOf<String?>()

        fun nextBuildReply(): DemoReply {
            val read = buildReads++
            if (read in failingReads) return failure
            val (status, outcome) = statuses[minOf(read, statuses.size - 1)]
            return DemoReply.ok(json(buildJson(status, outcome)))
        }
    }

    private fun repositoryFor(script: Script, logs: (String?) -> String): BuildsRepository = BuildsRepository(
        mockCloudflareApi {
            get("/accounts/{accountId}/builds/builds/{buildUuid}") { script.nextBuildReply() }
            get("/accounts/{accountId}/builds/builds/{buildUuid}/logs") { request ->
                script.logQueries += request.query["cursor"]
                DemoReply.ok(json(logs(request.query["cursor"])))
            }
            put("/accounts/{accountId}/builds/builds/{buildUuid}/cancel") {
                script.cancelCalls++
                DemoReply.ok(json("{}"))
            }
        },
    )

    private fun viewModel(
        script: Script,
        gateway: FakeGateway = FakeGateway(),
        logs: (String?) -> String = { """{"lines":[],"cursor":null}""" },
    ) = mainDispatcher.keep(BuildDetailViewModel::class.java) { BuildDetailViewModel(
        repository = repositoryFor(script, logs),
        gateway = gateway,
        accountId = ACCOUNT,
        buildUuid = BUILD,
        scriptName = SCRIPT,
        pollMillis = POLL_MS,
    ) }

    private suspend fun BuildDetailViewModel.awaitState(predicate: (BuildDetailUiState) -> Boolean): BuildDetailUiState =
        try {
            withTimeout(WAIT_MS) { state.first(predicate) }
        } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
            throw AssertionError("no matching state; last=${state.value}", timeout)
        }

    @Test
    fun `polling stops once the build is terminal and the log is marked finished`() = mainDispatcher.runVmTest {
        val script = Script(listOf("running" to null, "running" to null, "stopped" to "success"))
        val vm = viewModel(script)

        val finished = vm.awaitState { it.logsFinished }

        assertTrue((finished.build as UiState.Content).data.isTerminal)
        assertFalse(finished.isRefreshing)
        assertFalse(vm.state.value.canCancel)
    }

    @Test
    fun `a transient read failure keeps the screen polling until the build finishes`() = mainDispatcher.runVmTest {
        val script = Script(
            statuses = listOf("running" to null, "running" to null, "stopped" to "success"),
            failingReads = setOf(1),
        )
        val vm = viewModel(script)

        val finished = vm.awaitState { it.logsFinished }

        assertTrue((finished.build as UiState.Content).data.isTerminal)
        assertEquals(3, script.buildReads)
    }

    @Test
    fun `a fatal read failure stops polling and shows the error`() = mainDispatcher.runVmTest {
        val script = Script(
            statuses = listOf("running" to null),
            failingReads = setOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9),
            failure = DemoReply.failure(HttpStatusCode.NotFound, 10007, "no such build"),
        )
        val vm = viewModel(script)

        val failed = vm.awaitState { it.build is UiState.Failure && !it.isRefreshing }
        val readsAtFailure = script.buildReads

        assertTrue(failed.build is UiState.Failure)
        assertEquals(readsAtFailure, script.buildReads)
    }

    @Test
    fun `log lines accumulate across cursor reads without repeating`() = mainDispatcher.runVmTest {
        val script = Script(listOf("running" to null, "running" to null, "stopped" to "success"))
        val pages = mapOf(
            null to """{"lines":[[1,"one"],[2,"two"]],"cursor":"c2"}""",
            "c2" to """{"lines":[[3,"three"]],"cursor":"c3"}""",
            "c3" to """{"lines":[[4,"four"]],"cursor":"c4"}""",
        )
        val vm = viewModel(script, logs = { cursor -> pages[cursor] ?: """{"lines":[],"cursor":"c4"}""" })

        val done = vm.awaitState { it.logsFinished && it.logLines.size >= 4 }

        assertEquals(listOf("one", "two", "three", "four"), done.logLines)
    }

    @Test
    fun `cancel asks first, and confirming calls the cancel endpoint once`() = mainDispatcher.runVmTest {
        val script = Script(listOf("running" to null))
        val vm = viewModel(script)
        vm.awaitState { it.build is UiState.Content }

        vm.requestCancel()
        assertTrue(vm.state.value.showCancelConfirm)
        vm.dismissCancel()
        assertFalse(vm.state.value.showCancelConfirm)
        assertEquals(0, script.cancelCalls)

        vm.requestCancel()
        vm.confirmCancel()
        vm.awaitState { !it.isCancelling && it.message != null }

        assertEquals(1, script.cancelCalls)
        assertFalse(vm.state.value.showCancelConfirm)
    }

    @Test
    fun `a finished build never opens the cancel confirmation, even from a stale notification`() = mainDispatcher.runVmTest {
        val script = Script(listOf("stopped" to "success"))
        val vm = viewModel(script)
        vm.awaitState { it.build is UiState.Content && it.logsFinished }

        vm.requestCancel()

        assertFalse(vm.state.value.showCancelConfirm)
        assertEquals(0, script.cancelCalls)
    }

    @Test
    fun `a manual refresh of an active build ends its spinner after the read, not when the build finishes`() = mainDispatcher.runVmTest {
        val script = Script(listOf("running" to null))
        val vm = viewModel(script)
        vm.awaitState { it.build is UiState.Content && !it.isRefreshing }

        vm.refresh()
        val refreshed = vm.awaitState { !it.isRefreshing }

        assertTrue(refreshed.build is UiState.Content)
        assertFalse(refreshed.logsFinished)
    }

    @Test
    fun `watching goes through the gateway and reflects in the watched flag`() = mainDispatcher.runVmTest {
        val script = Script(listOf("running" to null))
        val gateway = FakeGateway()
        val vm = viewModel(script, gateway)
        vm.awaitState { it.build is UiState.Content }

        vm.startWatching()
        val watching = vm.awaitState { it.isWatched }
        vm.stopWatching()
        val stopped = vm.awaitState { !it.isWatched }

        assertEquals(listOf(BUILD), gateway.started)
        assertTrue(watching.isWatched)
        assertFalse(stopped.isWatched)
    }
}
