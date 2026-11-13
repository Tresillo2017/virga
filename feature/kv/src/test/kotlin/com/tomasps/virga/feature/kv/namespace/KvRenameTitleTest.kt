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
import org.junit.Rule
import org.junit.Test

private const val LONG_TITLE_CHARS = 513

/** Rename validation: each refusal names its own reason, and nothing is sent. */
@OptIn(ExperimentalCoroutinesApi::class)
class KvRenameTitleTest {

    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val main = MainDispatcherRule(dispatcher)

    private val store = KvDemoStore(TEST_NOW_EPOCH_SECONDS)
    private val repository = KvRepository(
        mockCloudflareApi { KvDemoRoutes(store) { TEST_NOW_EPOCH_SECONDS }.contribute(this) },
    )

    private fun viewModel() = KvNamespaceViewModel(
        namespaceId = KV_DEMO_NS_FLAGS,
        session = signedInSession(KV_DEMO_ACCOUNT_PERSONAL),
        repository = repository,
        changes = KvNamespaceChanges(),
    )

    private fun TestScope.recordMessages(vm: KvNamespaceViewModel): List<KvNamespaceEvent> {
        val received = mutableListOf<KvNamespaceEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { received += it } }
        return received
    }

    @Test
    fun `a title longer than 512 characters is refused with the too-long message`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        val events = recordMessages(vm)

        vm.rename("t".repeat(LONG_TITLE_CHARS))
        advanceUntilIdle()

        assertEquals(KvNamespaceEvent.Message(UiText.Resource(R.string.kv_error_title_too_long)), events.single())
        assertEquals("Feature flags", store.namespaces.get(store.namespaceKey(KV_DEMO_ACCOUNT_PERSONAL, KV_DEMO_NS_FLAGS))?.title)
    }

    @Test
    fun `a blank title is refused with the required message`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        val events = recordMessages(vm)

        vm.rename("   ")
        advanceUntilIdle()

        assertEquals(KvNamespaceEvent.Message(UiText.Resource(R.string.kv_error_title_required)), events.single())
    }
}
