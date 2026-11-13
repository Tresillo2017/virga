package com.tomasps.virga.feature.kv.namespace

import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.core.ui.state.UiText
import com.tomasps.virga.feature.kv.R
import com.tomasps.virga.feature.kv.changes.KvNamespaceChanges
import com.tomasps.virga.feature.kv.data.KvRepository
import com.tomasps.virga.feature.kv.demo.KV_DEMO_ACCOUNT_PERSONAL
import com.tomasps.virga.feature.kv.demo.KV_DEMO_NS_FLAGS
import com.tomasps.virga.feature.kv.demo.KvDemoRoutes
import com.tomasps.virga.feature.kv.demo.KvDemoStore
import com.tomasps.virga.feature.kv.testing.MainDispatcherRule
import com.tomasps.virga.feature.kv.testing.TEST_NOW_EPOCH_SECONDS
import com.tomasps.virga.feature.kv.testing.signedInSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** Rename and delete of one namespace: the success path, and the namespace being gone after a delete. */
@OptIn(ExperimentalCoroutinesApi::class)
class KvNamespaceActionsTest {

    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val main = MainDispatcherRule(dispatcher)

    private val store = KvDemoStore(TEST_NOW_EPOCH_SECONDS)
    private val repository = KvRepository(
        mockCloudflareApi { KvDemoRoutes(store) { TEST_NOW_EPOCH_SECONDS }.contribute(this) },
    )
    private val nsKey = store.namespaceKey(KV_DEMO_ACCOUNT_PERSONAL, KV_DEMO_NS_FLAGS)

    private fun viewModel() = KvNamespaceViewModel(
        namespaceId = KV_DEMO_NS_FLAGS,
        session = signedInSession(KV_DEMO_ACCOUNT_PERSONAL),
        repository = repository,
        changes = KvNamespaceChanges(),
    )

    private fun TestScope.recordEvents(vm: KvNamespaceViewModel): MutableList<KvNamespaceEvent> {
        val received = mutableListOf<KvNamespaceEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { received += it } }
        return received
    }

    @Test
    fun `a valid rename is stored, the title updates, and the user is told`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        val events = recordEvents(vm)

        vm.rename("  Flags v2  ")
        advanceUntilIdle()

        assertEquals(listOf<KvNamespaceEvent>(KvNamespaceEvent.Message(UiText.Resource(R.string.kv_message_namespace_renamed))), events)
        assertEquals("Flags v2", vm.state.value.title)
        assertEquals("Flags v2", store.namespaces.get(nsKey)?.title)
    }

    @Test
    fun `delete removes the namespace and its keys, then closes the screen`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        val events = recordEvents(vm)

        vm.delete()
        advanceUntilIdle()

        assertEquals(listOf<KvNamespaceEvent>(KvNamespaceEvent.Closed), events)
        assertNull(store.namespaces.get(nsKey))
        assertEquals(false, store.entries.items.value.keys.any { it.startsWith(store.keyPrefix(KV_DEMO_ACCOUNT_PERSONAL, KV_DEMO_NS_FLAGS)) })
    }
}
