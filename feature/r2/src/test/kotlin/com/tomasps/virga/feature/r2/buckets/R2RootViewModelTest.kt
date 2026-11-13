package com.tomasps.virga.feature.r2.buckets

import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.demo.InMemoryStore
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.feature.r2.api.R2BucketInfo
import com.tomasps.virga.feature.r2.api.R2Repository
import com.tomasps.virga.feature.r2.testing.FakeSessionRepository
import com.tomasps.virga.feature.r2.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import com.tomasps.virga.feature.r2.api.R2_BUCKET_NAME_MAX_LENGTH

private const val WAIT_MS = 5_000L

class R2RootViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @Test
    fun `the bucket list loads for the selected account`() = runBlocking {
        // Arrange
        val store = InMemoryStore(mapOf("alpha" to bucket("alpha")))
        val api = bucketApi(store)
        val viewModel = R2RootViewModel(FakeSessionRepository(), R2Repository(api))

        // Act
        val state = withTimeout(WAIT_MS) { viewModel.state.first { it.buckets is UiState.Content } }

        // Assert
        val content = state.buckets as UiState.Content
        assertEquals(listOf("alpha"), content.data.map { it.name })
    }

    @Test
    fun `showing the screen again reloads the list, so a bucket deleted meanwhile disappears`() = runBlocking {
        val store = InMemoryStore(mapOf("alpha" to bucket("alpha"), "beta" to bucket("beta")))
        val viewModel = R2RootViewModel(FakeSessionRepository(), R2Repository(bucketApi(store)))
        withTimeout(WAIT_MS) { viewModel.state.first { it.buckets is UiState.Content } }
        viewModel.onScreenShown()
        store.remove("alpha")

        viewModel.onScreenShown()

        val names = withTimeout(WAIT_MS) {
            viewModel.state.first { (it.buckets as? UiState.Content)?.data?.map { b -> b.name } == listOf("beta") }
        }.buckets
        assertEquals(listOf("beta"), (names as UiState.Content).data.map { it.name })
    }

    @Test
    fun `an invalid bucket name is rejected before any request is sent`() = runBlocking {
        // Arrange
        val store = InMemoryStore<R2BucketInfo>()
        var creates = 0
        val api = mockCloudflareApi {
            post("/accounts/{accountId}/r2/buckets") {
                creates++
                DemoReply.ok(buildJsonObject { put("name", "x") })
            }
            get("/accounts/{accountId}/r2/buckets") { DemoReply.ok(buildJsonObject {}) }
        }
        val viewModel = R2RootViewModel(FakeSessionRepository(), R2Repository(api))
        withTimeout(WAIT_MS) { viewModel.state.first { it.buckets != null } }
        viewModel.openCreate()
        viewModel.onCreateNameChanged("Bad_Name")

        // Act
        viewModel.submitCreate()

        // Assert
        val form = viewModel.state.value.create
        assertNotNull(form?.nameError)
        assertEquals(0, creates)
        assertTrue(store.items.value.isEmpty())
    }

    @Test
    fun `a character Cloudflare does not allow shows the name error while typing, before Create`() = runBlocking {
        val viewModel = loadedViewModel()
        viewModel.openCreate()

        viewModel.onCreateNameChanged("Bad_Name")

        assertNotNull(viewModel.state.value.create?.nameError)
    }

    @Test
    fun `the name error clears once the name only uses allowed characters`() = runBlocking {
        val viewModel = loadedViewModel()
        viewModel.openCreate()
        viewModel.onCreateNameChanged("bad_")

        viewModel.onCreateNameChanged("bad-")

        assertNull(viewModel.state.value.create?.nameError)
    }

    @Test
    fun `a name that is only too short is flagged on Create, not while typing`() = runBlocking {
        val viewModel = loadedViewModel()
        viewModel.openCreate()

        viewModel.onCreateNameChanged("ab")
        assertNull(viewModel.state.value.create?.nameError)

        viewModel.submitCreate()
        assertNotNull(viewModel.state.value.create?.nameError)
    }

    @Test
    fun `a name longer than the limit is flagged while typing`() = runBlocking {
        val viewModel = loadedViewModel()
        viewModel.openCreate()

        viewModel.onCreateNameChanged("a".repeat(R2_BUCKET_NAME_MAX_LENGTH + 1))

        assertNotNull(viewModel.state.value.create?.nameError)
    }

    private suspend fun loadedViewModel(): R2RootViewModel {
        val api = bucketApi(InMemoryStore(emptyMap()))
        val viewModel = R2RootViewModel(FakeSessionRepository(), R2Repository(api))
        withTimeout(WAIT_MS) { viewModel.state.first { it.buckets != null } }
        return viewModel
    }

    @Test
    fun `a valid name creates the bucket, closes the form and reloads the list`() = runBlocking {
        // Arrange
        val store = InMemoryStore<R2BucketInfo>()
        val api = bucketApi(store)
        val viewModel = R2RootViewModel(FakeSessionRepository(), R2Repository(api))
        withTimeout(WAIT_MS) { viewModel.state.first { it.buckets is UiState.Content } }
        viewModel.openCreate()
        viewModel.onCreateNameChanged("new-bucket")

        // Act
        viewModel.submitCreate()

        // Assert
        val state = withTimeout(WAIT_MS) { viewModel.state.first { it.create == null && it.buckets is UiState.Content } }
        assertEquals(listOf("new-bucket"), (state.buckets as UiState.Content).data.map { it.name })
        assertNotNull(state.message)
    }

    private fun bucket(name: String) = R2BucketInfo(name = name, location = "enam", storageClass = "Standard")

    /** A bucket API backed by [store], so creates show up on the next list call. */
    private fun bucketApi(store: InMemoryStore<R2BucketInfo>) = mockCloudflareApi {
        get("/accounts/{accountId}/r2/buckets") {
            DemoReply.ok(
                result = buildJsonObject {
                    put("buckets", buildJsonArray {
                        store.items.value.values.forEach { item ->
                            add(buildJsonObject { put("name", item.name); put("location", item.location.orEmpty()) })
                        }
                    })
                },
                resultInfo = buildJsonObject { put("per_page", 50) },
            )
        }
        post("/accounts/{accountId}/r2/buckets") { request ->
            val name = kotlinx.serialization.json.Json.parseToJsonElement(request.bodyText.orEmpty())
                .let { (it as kotlinx.serialization.json.JsonObject)["name"]!!.toString().trim('"') }
            store.put(name, bucket(name))
            DemoReply.ok(buildJsonObject { put("name", name) })
        }
    }

    @Test
    fun `a bucket page requested before the search changed never joins the new search`() = runBlocking {
        // Arrange
        val gate = CompletableDeferred<Unit>()
        val api = mockCloudflareApi {
            get("/accounts/{accountId}/r2/buckets") { request ->
                when {
                    request.query["cursor"] == "page-2" -> {
                        gate.await()
                        DemoReply.ok(buildJsonObject { put("buckets", buildJsonArray { add(buildJsonObject { put("name", "stale") }) }) }, resultInfo = buildJsonObject { put("per_page", 50) })
                    }
                    request.query["name_contains"] == "beta" -> DemoReply.ok(
                        buildJsonObject { put("buckets", buildJsonArray { add(buildJsonObject { put("name", "beta") }) }) },
                        resultInfo = buildJsonObject { put("per_page", 50) },
                    )
                    else -> DemoReply.ok(
                        buildJsonObject { put("buckets", buildJsonArray { add(buildJsonObject { put("name", "first") }) }) },
                        resultInfo = buildJsonObject { put("per_page", 50); put("cursor", "page-2") },
                    )
                }
            }
        }
        val viewModel = R2RootViewModel(FakeSessionRepository(), R2Repository(api))
        withTimeout(WAIT_MS) { viewModel.state.first { it.nextCursor == "page-2" } }
        viewModel.loadMore()

        // Act
        viewModel.onSearchChanged("beta")
        mainDispatcher.scheduler.advanceUntilIdle()
        withTimeout(WAIT_MS) { viewModel.state.first { (it.buckets as? UiState.Content)?.data?.map { b -> b.name } == listOf("beta") } }
        gate.complete(Unit)

        // Assert
        assertEquals(listOf("beta"), ((viewModel.state.value.buckets as UiState.Content).data).map { it.name })
        assertFalse(viewModel.state.value.isLoadingMore)
    }
}
