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
import com.tomasps.virga.feature.kv.files.KvFileStore
import com.tomasps.virga.feature.kv.testing.FakeKvFileStore
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
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.ZoneId

private const val KEY = "banner text"
private const val SLASHED_KEY = "users/42/profile?draft=1"
private const val SAVE_URI = "content://demo/documents/out.bin"

/** Download: the suggested file name and what happens when the document cannot be written. */
@OptIn(ExperimentalCoroutinesApi::class)
class KvDownloadTest {

    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val main = MainDispatcherRule(dispatcher)

    private val store = KvDemoStore(TEST_NOW_EPOCH_SECONDS)
    private val repository = KvRepository(
        mockCloudflareApi { KvDemoRoutes(store) { TEST_NOW_EPOCH_SECONDS }.contribute(this) },
    )

    private fun viewModel(key: String, files: KvFileStore) = KvKeyDetailViewModel(
        namespaceId = KV_DEMO_NS_FLAGS,
        keyName = key,
        session = signedInSession(KV_DEMO_ACCOUNT_PERSONAL),
        repository = repository,
        writer = KvKeyWriter(repository, KvWriteThrottle { TEST_NOW_EPOCH_SECONDS * 1_000L }) { TEST_NOW_EPOCH_SECONDS },
        changes = KvNamespaceChanges(),
        files = files,
        zone = ZoneId.of("UTC"),
        nowEpochSeconds = { TEST_NOW_EPOCH_SECONDS },
    )

    private fun TestScope.recordEvents(vm: KvKeyDetailViewModel): MutableList<KvKeyEvent> {
        val received = mutableListOf<KvKeyEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { received += it } }
        return received
    }

    @Test
    fun `download offers the key name as the file name, with slashes turned into underscores`() = runTest(dispatcher) {
        val vm = viewModel(SLASHED_KEY, FakeKvFileStore())
        advanceUntilIdle()
        val events = recordEvents(vm)

        vm.prepareDownload()
        advanceUntilIdle()

        assertEquals(KvKeyEvent.DownloadReady("users_42_profile?draft=1"), events.single())
    }

    @Test
    fun `saving the download writes the value to the chosen document and says so`() = runTest(dispatcher) {
        val files = FakeKvFileStore()
        val vm = viewModel(KEY, files)
        advanceUntilIdle()
        val events = recordEvents(vm)
        vm.prepareDownload()
        advanceUntilIdle()

        vm.saveDownload(SAVE_URI)
        advanceUntilIdle()

        assertEquals("Scheduled maintenance on Sunday", String(files.written.getValue(SAVE_URI)))
        assertEquals(KvKeyEvent.Message(UiText.Resource(R.string.kv_message_downloaded)), events.last())
    }

    @Test
    fun `a document that cannot be written reports the failure`() = runTest(dispatcher) {
        val refusing = object : KvFileStore by FakeKvFileStore() {
            override suspend fun writeBytes(uri: String, bytes: ByteArray): Boolean = false
        }
        val vm = viewModel(KEY, refusing)
        advanceUntilIdle()
        val events = recordEvents(vm)
        vm.prepareDownload()
        advanceUntilIdle()

        vm.saveDownload(SAVE_URI)
        advanceUntilIdle()

        assertEquals(KvKeyEvent.Message(UiText.Resource(R.string.kv_error_download_failed)), events.last())
    }
}
