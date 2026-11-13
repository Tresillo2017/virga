package com.tomasps.virga.feature.kv.importer

import com.tomasps.virga.core.common.DispatcherProvider
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.feature.kv.changes.KvNamespaceChanges
import com.tomasps.virga.feature.kv.data.KvRepository
import com.tomasps.virga.feature.kv.demo.KV_DEMO_ACCOUNT_PERSONAL
import com.tomasps.virga.feature.kv.demo.KV_DEMO_NS_FLAGS
import com.tomasps.virga.feature.kv.demo.KvDemoRoutes
import com.tomasps.virga.feature.kv.demo.KvDemoStore
import com.tomasps.virga.feature.kv.testing.FakeKvFileStore
import com.tomasps.virga.feature.kv.testing.MainDispatcherRule
import com.tomasps.virga.feature.kv.testing.TEST_NOW_EPOCH_SECONDS
import com.tomasps.virga.feature.kv.testing.signedInSession
import com.tomasps.virga.feature.kv.testing.testDispatchers
import com.tomasps.virga.feature.kv.value.BulkImportProblem
import com.tomasps.virga.feature.kv.value.PairProblem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val FILE_URI = "content://demo/documents/import.json"
private const val VALID_FILE = """[{"key":"imported/a","value":"1"},{"key":".","value":"x"},{"key":"imported/b"}]"""
private const val NOT_AN_ARRAY = """{"key":"a","value":"1"}"""

/**
 * Runs on virtual time. The parse step, which the view model moves off the main thread, uses the same test scheduler
 * as runTest, so advanceUntilIdle finishes every step. Nothing waits on the real clock, so load cannot make it flaky.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KvImportViewModelTest {

    private val scheduler = StandardTestDispatcher()

    @get:Rule
    val main = MainDispatcherRule(scheduler)

    private val store = KvDemoStore(TEST_NOW_EPOCH_SECONDS)
    private val repository = KvRepository(
        mockCloudflareApi { KvDemoRoutes(store) { TEST_NOW_EPOCH_SECONDS }.contribute(this) },
    )
    private val files = FakeKvFileStore()

    private fun viewModel() = KvImportViewModel(
        namespaceId = KV_DEMO_NS_FLAGS,
        session = signedInSession(KV_DEMO_ACCOUNT_PERSONAL),
        repository = repository,
        changes = KvNamespaceChanges(),
        files = files,
        dispatchers = testDispatchers(scheduler),
    )

    @Test
    fun `the preview counts valid pairs and lists the ones refused locally`() = runTest(scheduler) {
        // Arrange
        files.documents[FILE_URI] = VALID_FILE.encodeToByteArray()
        val vm = viewModel()

        // Act
        vm.pickFile(FILE_URI)
        advanceUntilIdle()
        val state = vm.state.value

        // Assert
        assertTrue(state.hasFile)
        assertEquals(1, state.preview?.pairCount)
        assertEquals(listOf(PairProblem.KEY_DOT_SEGMENT, PairProblem.MISSING_VALUE), state.preview?.rejected?.map { it.problem })
        assertEquals(1, state.preview?.chunkCount)
    }

    @Test
    fun `starting the import writes the valid pairs and reports what was written`() = runTest(scheduler) {
        // Arrange
        files.documents[FILE_URI] = VALID_FILE.encodeToByteArray()
        val vm = viewModel()
        vm.pickFile(FILE_URI)
        advanceUntilIdle()

        // Act
        vm.startImport()
        advanceUntilIdle()
        val report = vm.state.value.report

        // Assert
        assertEquals(1, report?.written)
        assertEquals(emptyList<String>(), report?.unsuccessfulKeys)
        assertEquals(2, report?.rejected?.size)
        assertTrue(store.entries.get(store.entryKey(KV_DEMO_ACCOUNT_PERSONAL, KV_DEMO_NS_FLAGS, "imported/a")) != null)
        assertNull(store.entries.get(store.entryKey(KV_DEMO_ACCOUNT_PERSONAL, KV_DEMO_NS_FLAGS, ".")))
    }

    @Test
    fun `a file that is not an array is refused as a whole and nothing can be imported`() = runTest(scheduler) {
        // Arrange
        files.documents[FILE_URI] = NOT_AN_ARRAY.encodeToByteArray()
        val vm = viewModel()

        // Act
        vm.pickFile(FILE_URI)
        advanceUntilIdle()

        // Assert
        assertEquals(BulkImportProblem.NOT_AN_ARRAY, vm.state.value.refusal)
        assertNull(vm.state.value.preview)
    }
}

/** Both dispatchers the view model asks for are the test scheduler, so virtual time covers the parse as well. */

