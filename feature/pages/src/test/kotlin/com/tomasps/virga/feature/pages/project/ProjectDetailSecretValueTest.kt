package com.tomasps.virga.feature.pages.project

import com.tomasps.virga.core.auth.session.CloudflareAccount
import com.tomasps.virga.core.auth.session.SessionState
import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.core.ui.state.UiText
import com.tomasps.virga.feature.pages.R
import com.tomasps.virga.feature.pages.data.PagesProjectsRepository
import com.tomasps.virga.feature.pages.logic.EnvVarEntry
import com.tomasps.virga.feature.pages.logic.EnvVarType
import com.tomasps.virga.feature.pages.testing.FakeSessionRepository
import com.tomasps.virga.feature.pages.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private const val WAIT_MS = 5_000L

class ProjectDetailSecretValueTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val session = FakeSessionRepository(
        SessionState.SignedIn(listOf(CloudflareAccount("a1", "Alpha")), "a1", isDemo = false),
    )

    private fun viewModelWith(onPatch: () -> Unit): ProjectDetailViewModel {
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/pages/projects/{projectName}") {
                DemoReply.ok(buildJsonObject { put("id", "p"); put("name", "lantern-docs") })
            }
            patch("/accounts/{accountId}/pages/projects/{projectName}") {
                onPatch()
                DemoReply.ok(JsonNull)
            }
        }
        return ProjectDetailViewModel("lantern-docs", session, PagesProjectsRepository(api))
    }

    @Test
    fun `turning a plain variable into a secret without a value is refused and nothing is sent`() = runBlocking {
        var patches = 0
        val viewModel = viewModelWith(onPatch = { patches++ })
        withTimeout(WAIT_MS) { viewModel.state.first { it.project is UiState.Content } }
        val plain = EnvVarEntry("SITE_NAME", EnvVarType.PLAIN, "Lantern")

        viewModel.saveEnvVar("production", "SITE_NAME", EnvVarType.SECRET, value = "", existing = plain)

        val refused = withTimeout(WAIT_MS) { viewModel.state.first { it.message != null } }
        assertEquals(UiText.Resource(R.string.pages_message_env_value_required), refused.message)
        assertEquals(0, patches)
    }

    @Test
    fun `an existing secret saved with a blank value is not refused, it reports nothing changed`() = runBlocking {
        var patches = 0
        val viewModel = viewModelWith(onPatch = { patches++ })
        withTimeout(WAIT_MS) { viewModel.state.first { it.project is UiState.Content } }
        val secret = EnvVarEntry("TOKEN", EnvVarType.SECRET, value = null)

        viewModel.saveEnvVar("production", "TOKEN", EnvVarType.SECRET, value = "", existing = secret)

        val outcome = withTimeout(WAIT_MS) { viewModel.state.first { it.message != null } }
        assertEquals(UiText.Resource(R.string.pages_message_env_unchanged), outcome.message)
        assertEquals(0, patches)
    }
}
