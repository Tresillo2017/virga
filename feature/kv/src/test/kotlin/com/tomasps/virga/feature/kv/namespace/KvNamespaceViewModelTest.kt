package com.tomasps.virga.feature.kv.namespace

import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.feature.kv.changes.KvNamespaceChanges
import com.tomasps.virga.feature.kv.data.KvRepository
import com.tomasps.virga.feature.kv.demo.KV_DEMO_ACCOUNT_PERSONAL
import com.tomasps.virga.feature.kv.demo.KV_DEMO_NS_SESSIONS
import com.tomasps.virga.feature.kv.demo.KvDemoEntry
import com.tomasps.virga.feature.kv.demo.KvDemoRoutes
import com.tomasps.virga.feature.kv.demo.KvDemoStore
import com.tomasps.virga.feature.kv.testing.MainDispatcherRule
import com.tomasps.virga.feature.kv.testing.TEST_NOW_EPOCH_SECONDS
import com.tomasps.virga.feature.kv.testing.awaitTrue
import com.tomasps.virga.feature.kv.testing.signedInSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val SELECTED_ONE = "bulk/one"
private const val SELECTED_TWO = "bulk/two"
private const val KEPT = "bulk/kept"

class KvNamespaceViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val store = KvDemoStore(TEST_NOW_EPOCH_SECONDS)
    private val repository = KvRepository(
        mockCloudflareApi { KvDemoRoutes(store) { TEST_NOW_EPOCH_SECONDS }.contribute(this) },
    )
    private val changes = KvNamespaceChanges()

    private fun viewModel() = KvNamespaceViewModel(
        namespaceId = KV_DEMO_NS_SESSIONS,
        session = signedInSession(KV_DEMO_ACCOUNT_PERSONAL),
        repository = repository,
        changes = changes,
        prefixDebounceMillis = 0L,
    )

    private fun put(name: String) = store.entries.put(
        store.entryKey(KV_DEMO_ACCOUNT_PERSONAL, KV_DEMO_NS_SESSIONS, name),
        KvDemoEntry("v".encodeToByteArray(), null, null),
    )

    @Test
    fun `the title and the keys of the namespace load on open`() = runBlocking {
        // Arrange and Act
        val vm = viewModel()
        awaitTrue { vm.state.value.keys is UiState.Content }
        val state = vm.state.value

        // Assert
        assertEquals("Session cache", state.title)
        assertTrue((state.keys as UiState.Content).data.any { it.name == "session:7f3a0c" })
    }

    @Test
    fun `a prefix narrows the key list`() = runBlocking {
        // Arrange
        val vm = viewModel()
        awaitTrue { vm.state.value.keys is UiState.Content }

        // Act
        vm.setPrefix("session:")
        awaitTrue { vm.state.value.prefix == "session:" && vm.state.value.keys is UiState.Content }
        val state = vm.state.value

        // Assert
        assertEquals(listOf("session:7f3a0c"), (state.keys as UiState.Content).data.map { it.name })
    }

    @Test
    fun `bulk delete removes only the selected keys`() = runBlocking {
        // Arrange
        listOf(SELECTED_ONE, SELECTED_TWO, KEPT).forEach { put(it) }
        val vm = viewModel()
        awaitTrue { vm.state.value.keys is UiState.Content }
        vm.setPrefix("bulk/")
        awaitTrue { vm.state.value.prefix == "bulk/" && vm.state.value.keys is UiState.Content }
        vm.toggleSelected(SELECTED_ONE)
        vm.toggleSelected(SELECTED_TWO)

        // Act
        vm.confirmBulkDelete()
        awaitTrue { !vm.state.value.isBusy && vm.state.value.selected.isEmpty() }

        // Assert
        assertNull(store.entries.get(store.entryKey(KV_DEMO_ACCOUNT_PERSONAL, KV_DEMO_NS_SESSIONS, SELECTED_ONE)))
        assertNull(store.entries.get(store.entryKey(KV_DEMO_ACCOUNT_PERSONAL, KV_DEMO_NS_SESSIONS, SELECTED_TWO)))
        assertTrue(store.entries.get(store.entryKey(KV_DEMO_ACCOUNT_PERSONAL, KV_DEMO_NS_SESSIONS, KEPT)) != null)
    }

    @Test
    fun `a page requested for the old prefix never lands on the new prefix's list`() = runBlocking {
        // Arrange: the second page of the old prefix is held until the test releases it
        val releaseOldPage = CompletableDeferred<Unit>()
        val api = mockCloudflareApi {
            get(KEYS_PATH) { request ->
                when {
                    request.query["prefix"] == "new" -> DemoReply.ok(keyList("new/x"))
                    request.query["cursor"] == "page-2" -> {
                        releaseOldPage.await()
                        DemoReply.ok(keyList("old/stale"))
                    }
                    else -> DemoReply.ok(keyList("old/a"), resultInfo = buildJsonObject { put("cursor", "page-2") })
                }
            }
        }
        val vm = KvNamespaceViewModel(
            namespaceId = KV_DEMO_NS_SESSIONS,
            session = signedInSession(KV_DEMO_ACCOUNT_PERSONAL),
            repository = KvRepository(api),
            changes = changes,
            prefixDebounceMillis = 0L,
        )
        awaitTrue { keyNames(vm) == listOf("old/a") }
        vm.loadMore()
        awaitTrue { vm.state.value.isLoadingMore }

        // Act
        vm.setPrefix("new")
        releaseOldPage.complete(Unit)
        awaitTrue { keyNames(vm) == listOf("new/x") }
        delay(SETTLE_MILLIS)

        // Assert
        assertEquals(listOf("new/x"), keyNames(vm))
        assertFalse(vm.state.value.isLoadingMore)
    }

    private fun keyList(vararg names: String) = buildJsonArray {
        names.forEach { name -> add(buildJsonObject { put("name", name) }) }
    }

    private fun keyNames(vm: KvNamespaceViewModel): List<String>? =
        (vm.state.value.keys as? UiState.Content)?.data?.map { it.name }

    private companion object {
        const val KEYS_PATH = "/accounts/{accountId}/storage/kv/namespaces/{namespaceId}/keys"
        const val SETTLE_MILLIS = 50L
    }
}
