package com.tomasps.virga.feature.kv.importer

import com.tomasps.virga.core.network.demo.DemoReply
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
import com.tomasps.virga.feature.kv.testing.testDispatchers
import com.tomasps.virga.feature.kv.value.BulkImportProblem
import com.tomasps.virga.feature.kv.value.KV_BULK_MAX_BYTES
import com.tomasps.virga.feature.kv.value.KV_BULK_MAX_PAIRS
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val FILE_URI = "content://demo/documents/report.json"
private const val BULK_PATH = "/accounts/{accountId}/storage/kv/namespaces/{namespaceId}/bulk"
private const val BULK_FAILURE_CODE = 10_000
private const val TWO_VALID = """[{"key":"good/one","value":"1"},{"key":"bad/two","value":"2"}]"""

/** Import results beyond the happy path: keys the server refused, failed batches, double taps, and the file limits. */
@OptIn(ExperimentalCoroutinesApi::class)
class KvImportReportTest {

    private val scheduler = StandardTestDispatcher()

    @get:Rule
    val main = MainDispatcherRule(scheduler)

    private val store = KvDemoStore(TEST_NOW_EPOCH_SECONDS)
    private val files = FakeKvFileStore()

    private fun viewModel(api: KvRepository, fileStore: KvFileStore = files) = KvImportViewModel(
        namespaceId = KV_DEMO_NS_FLAGS,
        session = signedInSession(KV_DEMO_ACCOUNT_PERSONAL),
        repository = api,
        changes = KvNamespaceChanges(),
        files = fileStore,
        dispatchers = testDispatchers(scheduler),
    )

    private fun demoRepository() = KvRepository(
        mockCloudflareApi { KvDemoRoutes(store) { TEST_NOW_EPOCH_SECONDS }.contribute(this) },
    )

    private fun TestScope.recordMessages(vm: KvImportViewModel): MutableList<KvImportEvent> {
        val received = mutableListOf<KvImportEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { received += it } }
        return received
    }

    @Test
    fun `a key the server refuses is listed as unsuccessful and the rest are counted as written`() = runTest(scheduler) {
        // Arrange: the server accepts one of the two keys
        val repository = KvRepository(
            mockCloudflareApi {
                put(BULK_PATH) {
                    DemoReply.ok(
                        buildJsonObject {
                            put("successful_key_count", 1)
                            putJsonArray("unsuccessful_keys") { add(JsonPrimitive("bad/two")) }
                        },
                    )
                }
            },
        )
        files.documents[FILE_URI] = TWO_VALID.encodeToByteArray()
        val vm = viewModel(repository)
        vm.pickFile(FILE_URI)
        advanceUntilIdle()

        // Act
        vm.startImport()
        advanceUntilIdle()

        // Assert
        val report = vm.state.value.report!!
        assertEquals(1, report.written)
        assertEquals(listOf("bad/two"), report.unsuccessfulKeys)
        assertEquals(0, report.failedChunks)
        assertEquals(false, vm.state.value.isImporting)
    }

    @Test
    fun `a batch the server fails is reported with its keys, and the import still finishes`() = runTest(scheduler) {
        // Arrange
        val repository = KvRepository(
            mockCloudflareApi {
                put(BULK_PATH) { DemoReply.failure(HttpStatusCode.InternalServerError, BULK_FAILURE_CODE, "unavailable") }
            },
        )
        files.documents[FILE_URI] = TWO_VALID.encodeToByteArray()
        val vm = viewModel(repository)
        vm.pickFile(FILE_URI)
        advanceUntilIdle()
        val messages = recordMessages(vm)

        // Act
        vm.startImport()
        advanceUntilIdle()

        // Assert
        val report = vm.state.value.report!!
        assertEquals(0, report.written)
        assertEquals(1, report.failedChunks)
        assertEquals(listOf("good/one", "bad/two"), report.unsuccessfulKeys)
        assertEquals(1, messages.size)
        assertTrue((messages.single() as KvImportEvent.Message).text is UiText)
    }

    @Test
    fun `a second tap on Import while the first run is going sends the batches once`() = runTest(scheduler) {
        // Arrange
        var sends = 0
        val repository = KvRepository(
            mockCloudflareApi {
                put(BULK_PATH) {
                    sends++
                    DemoReply.ok(buildJsonObject { put("successful_key_count", 1); putJsonArray("unsuccessful_keys") {} })
                }
            },
        )
        files.documents[FILE_URI] = TWO_VALID.encodeToByteArray()
        val vm = viewModel(repository)
        vm.pickFile(FILE_URI)
        advanceUntilIdle()

        // Act
        vm.startImport()
        vm.startImport()
        advanceUntilIdle()

        // Assert
        assertEquals(1, sends)
    }

    @Test
    fun `a file with more than 10,000 pairs is refused as a whole and nothing is previewed`() = runTest(scheduler) {
        // Arrange
        val tooMany = (1..(KV_BULK_MAX_PAIRS + 1)).joinToString(",", "[", "]") { """{"key":"k$it","value":"v"}""" }
        files.documents[FILE_URI] = tooMany.encodeToByteArray()
        val vm = viewModel(demoRepository())

        // Act
        vm.pickFile(FILE_URI)
        advanceUntilIdle()

        // Assert
        assertEquals(BulkImportProblem.TOO_MANY_PAIRS, vm.state.value.refusal)
        assertNull(vm.state.value.preview)
    }

    @Test
    fun `a file over 100 MB is refused before it is read`() = runTest(scheduler) {
        // Arrange: a document whose size is just over the limit, and a store that must never be asked for its bytes
        val oversized = object : KvFileStore by files {
            override suspend fun sizeOf(uri: String): Long = KV_BULK_MAX_BYTES + 1
            override suspend fun readBytes(uri: String, maxBytes: Long): ByteArray? =
                error("an oversized file must not be read")
        }
        val vm = viewModel(demoRepository(), oversized)
        val messages = recordMessages(vm)

        // Act
        vm.pickFile(FILE_URI)
        advanceUntilIdle()

        // Assert
        assertEquals(UiText.Resource(R.string.kv_error_import_file_too_large), (messages.single() as KvImportEvent.Message).text)
        assertNull(vm.state.value.preview)
        assertTrue(vm.state.value.hasFile)
    }
}
