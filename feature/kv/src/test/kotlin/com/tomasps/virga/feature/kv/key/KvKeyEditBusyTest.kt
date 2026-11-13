package com.tomasps.virga.feature.kv.key

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
import com.tomasps.virga.feature.kv.value.KvWriteThrottle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private const val KEY = "checkout/new-flow"
private const val CLOCK_MILLIS = TEST_NOW_EPOCH_SECONDS * 1_000L

/** Edits that re-send the whole value: a second tap during the first edit's checks must not start a second write. */
@OptIn(ExperimentalCoroutinesApi::class)
class KvKeyEditBusyTest {

    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val main = MainDispatcherRule(dispatcher)

    private val store = KvDemoStore(TEST_NOW_EPOCH_SECONDS)
    private val repository = KvRepository(
        mockCloudflareApi { KvDemoRoutes(store) { TEST_NOW_EPOCH_SECONDS }.contribute(this) },
    )

    private fun viewModel() = KvKeyDetailViewModel(
        namespaceId = KV_DEMO_NS_FLAGS,
        keyName = KEY,
        session = signedInSession(KV_DEMO_ACCOUNT_PERSONAL),
        repository = repository,
        writer = KvKeyWriter(repository, KvWriteThrottle { CLOCK_MILLIS }) { TEST_NOW_EPOCH_SECONDS },
        changes = KvNamespaceChanges(),
        files = com.tomasps.virga.feature.kv.testing.FakeKvFileStore(),
        zone = java.time.ZoneId.of("UTC"),
        nowEpochSeconds = { TEST_NOW_EPOCH_SECONDS },
    )

    private fun TestScope.recordEvents(vm: KvKeyDetailViewModel): List<KvKeyEvent> {
        val received = mutableListOf<KvKeyEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { received += it } }
        return received
    }

    @Test
    fun `two metadata saves tapped together write once and report one success`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        val events = recordEvents(vm)
        vm.openMetadataEditor()
        vm.updateMetadataText("{\"owner\":\"busy-test\"}")

        vm.saveMetadata()
        vm.saveMetadata()
        advanceUntilIdle()

        assertEquals(listOf<KvKeyEvent>(KvKeyEvent.Message(UiText.Resource(R.string.kv_message_value_saved))), events)
        val expected: JsonElement = buildJsonObject { put("owner", "busy-test") }
        assertEquals(expected, store.entries.get(store.entryKey(KV_DEMO_ACCOUNT_PERSONAL, KV_DEMO_NS_FLAGS, KEY))?.metadata)
        assertEquals(false, vm.state.value.isBusy)
    }
}
