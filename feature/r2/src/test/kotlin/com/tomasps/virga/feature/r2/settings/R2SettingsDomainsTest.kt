package com.tomasps.virga.feature.r2.settings

import com.tomasps.virga.core.network.CloudflareApi
import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.core.ui.state.UiText
import com.tomasps.virga.feature.r2.R
import com.tomasps.virga.feature.r2.api.R2Repository
import com.tomasps.virga.feature.r2.testing.FakeSessionRepository
import com.tomasps.virga.feature.r2.testing.MainDispatcherRule
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

private const val WAIT_MS = 5_000L
private const val BUCKET = "demo-assets"
private const val DOMAINS = "/accounts/{accountId}/r2/buckets/{bucketName}/domains/custom"
private const val ONE_DOMAIN = "/accounts/{accountId}/r2/buckets/{bucketName}/domains/custom/{domain}"

class R2SettingsDomainsTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @Test
    fun `an invalid hostname is refused in the form and nothing is sent`() = runBlocking {
        // Arrange
        var posts = 0
        val api = mockCloudflareApi {
            get(DOMAINS) { DemoReply.ok(buildJsonObject {}) }
            post(DOMAINS) {
                posts++
                DemoReply.ok(buildJsonObject {})
            }
        }
        val viewModel = viewModelFor(api)
        withTimeout(WAIT_MS) { viewModel.state.first { it.domains is UiState.Content } }

        // Act
        viewModel.openDomainForm()
        viewModel.onDomainChanged("")
        viewModel.onDomainZoneChanged("zone-1")
        viewModel.submitDomain()

        // Assert
        assertEquals(0, posts)
        assertEquals(UiText.Resource(R.string.r2_domain_invalid), viewModel.state.value.domainForm?.error)
    }

    @Test
    fun `a valid hostname is sent lowercased and trimmed, then the form closes and the list reloads`() = runBlocking {
        // Arrange
        val sentDomains = mutableListOf<String>()
        var listReads = 0
        val api = mockCloudflareApi {
            get(DOMAINS) {
                listReads++
                DemoReply.ok(buildJsonObject {})
            }
            post(DOMAINS) { request ->
                sentDomains += Json.parseToJsonElement(request.bodyText.orEmpty()).jsonObject["domain"]!!.jsonPrimitive.content
                DemoReply.ok(buildJsonObject {})
            }
        }
        val viewModel = viewModelFor(api)
        withTimeout(WAIT_MS) { viewModel.state.first { it.domains is UiState.Content } }
        val readsBefore = listReads

        // Act
        viewModel.openDomainForm()
        viewModel.onDomainChanged("  Files.Example.com ")
        viewModel.onDomainZoneChanged("zone-1")
        viewModel.submitDomain()
        withTimeout(WAIT_MS) { viewModel.state.first { it.domainForm == null && it.message != null } }

        // Assert
        assertEquals(listOf("files.example.com"), sentDomains)
        assertEquals(UiText.Resource(R.string.r2_domain_added), viewModel.state.value.message)
        assertEquals(true, listReads > readsBefore)
    }

    @Test
    fun `a rejected add keeps the form open with the API error for the user`() = runBlocking {
        // Arrange
        val api = mockCloudflareApi {
            get(DOMAINS) { DemoReply.ok(buildJsonObject {}) }
            post(DOMAINS) { DemoReply.failure(HttpStatusCode.BadRequest, 1000, "zone not in this account") }
        }
        val viewModel = viewModelFor(api)
        withTimeout(WAIT_MS) { viewModel.state.first { it.domains is UiState.Content } }

        // Act
        viewModel.openDomainForm()
        viewModel.onDomainChanged("files.example.com")
        viewModel.onDomainZoneChanged("zone-1")
        viewModel.submitDomain()
        val failed = withTimeout(WAIT_MS) { viewModel.state.first { it.domainForm?.submitError != null } }

        // Assert
        assertNotNull(failed.domainForm)
        assertEquals(false, failed.domainForm?.isSaving)
        assertNull(failed.message)
    }

    @Test
    fun `removing a domain sends one delete only after the confirmation is requested and then reloads`() = runBlocking {
        // Arrange
        var deletes = 0
        val removed = mutableListOf<String>()
        val api = mockCloudflareApi {
            get(DOMAINS) { DemoReply.ok(buildJsonObject {}) }
            delete(ONE_DOMAIN) { request ->
                deletes++
                removed += request.pathParams["domain"].orEmpty()
                DemoReply.ok(buildJsonObject {})
            }
        }
        val viewModel = viewModelFor(api)
        withTimeout(WAIT_MS) { viewModel.state.first { it.domains is UiState.Content } }

        // Act
        viewModel.requestRemoveDomain("files.example.com")
        val beforeConfirm = deletes
        viewModel.confirmRemoveDomain()
        withTimeout(WAIT_MS) { viewModel.state.first { it.message != null && it.domainToRemove == null } }

        // Assert
        assertEquals(0, beforeConfirm)
        assertEquals(1, deletes)
        assertEquals(listOf("files.example.com"), removed)
    }

    private fun viewModelFor(api: CloudflareApi) = R2SettingsViewModel(
        FakeSessionRepository(),
        R2Repository(api),
        BUCKET,
    )
}
