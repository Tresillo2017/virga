package com.tomasps.virga.feature.builds.ui

import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.feature.builds.api.BuildConfigEdit
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val ACCOUNT = "acc-1"
private const val SCRIPT = "api-worker"
private const val TAG = "tag-1"
private const val WAIT_MS = 5_000L

class BuildConfigViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    /** Counts PATCH calls, so a test can prove an unchanged save sends nothing. */
    private class Calls {
        var patches = 0
    }

    private fun viewModel(
        previewsFail: Boolean = false,
        configFails: Boolean = false,
        calls: Calls = Calls(),
    ): BuildConfigViewModel {
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/workers/scripts") {
                DemoReply.ok(buildJsonArray { add(buildJsonObject { put("id", SCRIPT); put("tag", TAG) }) })
            }
            get("/accounts/{accountId}/builds/workers/{workerTag}") {
                if (configFails) {
                    DemoReply.failure(HttpStatusCode.InternalServerError, 500, "boom")
                } else {
                    DemoReply.ok(buildJsonObject { put("previews_enabled", true) })
                }
            }
            get("/accounts/{accountId}/builds/workers/{workerTag}/previews") {
                if (previewsFail) {
                    DemoReply.failure(HttpStatusCode.InternalServerError, 500, "boom")
                } else {
                    DemoReply.ok(JsonArray(emptyList()))
                }
            }
            patch("/accounts/{accountId}/builds/workers/{workerTag}") {
                calls.patches++
                DemoReply.ok(buildJsonObject { put("previews_enabled", false) })
            }
        }
        return mainDispatcher.keep(BuildConfigViewModel::class.java) { BuildConfigViewModel(BuildsRepository(api), ACCOUNT, SCRIPT) }
    }

    private suspend fun BuildConfigViewModel.awaitLoaded(): UiState<BuildConfigContent> =
        withTimeout(WAIT_MS) { state.first { it.content !is UiState.Loading }.content }

    @Test
    fun `a failed previews read keeps the settings on screen with an error for the previews`() = mainDispatcher.runVmTest {
        val vm = viewModel(previewsFail = true)

        val content = (vm.awaitLoaded() as UiState.Content).data

        assertEquals(true, content.config.previewsEnabled)
        assertTrue(content.previews.isEmpty())
        assertNotNull(content.previewsError)
    }

    @Test
    fun `loaded settings and previews carry no error`() = mainDispatcher.runVmTest {
        val vm = viewModel()

        val content = (vm.awaitLoaded() as UiState.Content).data

        assertNull(content.previewsError)
    }

    @Test
    fun `a failed settings read fails the screen`() = mainDispatcher.runVmTest {
        val vm = viewModel(configFails = true)

        assertTrue(vm.awaitLoaded() is UiState.Failure)
    }

    @Test
    fun `saving nothing changed sends no PATCH and reports saved`() = mainDispatcher.runVmTest {
        val calls = Calls()
        val vm = viewModel(calls = calls)
        vm.awaitLoaded()

        vm.save(BuildConfigEdit())

        assertEquals(0, calls.patches)
        assertTrue(vm.state.value.saved)
    }

    @Test
    fun `the saved event is one-shot, so a second edit is not closed by the first save`() = mainDispatcher.runVmTest {
        val calls = Calls()
        val vm = viewModel(calls = calls)
        vm.awaitLoaded()

        vm.save(BuildConfigEdit(buildCommand = "npm run build:prod"))
        vm.state.first { it.saved }
        vm.savedShown()

        assertFalse(vm.state.value.saved)
        assertEquals(1, calls.patches)
    }
}
