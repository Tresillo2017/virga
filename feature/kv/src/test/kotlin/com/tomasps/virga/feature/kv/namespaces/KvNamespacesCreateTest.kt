package com.tomasps.virga.feature.kv.namespaces

import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.core.ui.state.UiText
import com.tomasps.virga.feature.kv.R
import com.tomasps.virga.feature.kv.changes.KvNamespaceChanges
import com.tomasps.virga.feature.kv.data.KvJurisdiction
import com.tomasps.virga.feature.kv.data.KvRepository
import com.tomasps.virga.feature.kv.demo.KV_DEMO_ACCOUNT_PERSONAL
import com.tomasps.virga.feature.kv.demo.KvDemoRoutes
import com.tomasps.virga.feature.kv.demo.KvDemoStore
import com.tomasps.virga.feature.kv.testing.MainDispatcherRule
import com.tomasps.virga.feature.kv.testing.TEST_NOW_EPOCH_SECONDS
import com.tomasps.virga.feature.kv.testing.signedInSession
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val LONG_TITLE_CHARS = 513
private const val CREATE_FAILURE_CODE = 10_000

/** Creating a namespace: each refusal names its reason, a success lists the new namespace, and a failure says why. */
@OptIn(ExperimentalCoroutinesApi::class)
class KvNamespacesCreateTest {

    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val main = MainDispatcherRule(dispatcher)

    private val store = KvDemoStore(TEST_NOW_EPOCH_SECONDS)
    private val repository = KvRepository(
        mockCloudflareApi { KvDemoRoutes(store) { TEST_NOW_EPOCH_SECONDS }.contribute(this) },
    )

    private fun viewModel(api: KvRepository = repository) = KvNamespacesViewModel(
        session = signedInSession(KV_DEMO_ACCOUNT_PERSONAL),
        repository = api,
        changes = KvNamespaceChanges(),
    )

    private fun TestScope.recordMessages(vm: KvNamespacesViewModel): MutableList<UiText> {
        val received = mutableListOf<UiText>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.messages.collect { received += it } }
        return received
    }

    @Test
    fun `a blank title is refused and nothing is created`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        val messages = recordMessages(vm)
        val before = store.namespaces.items.value.size

        vm.create("   ")
        advanceUntilIdle()

        assertEquals(listOf<UiText>(UiText.Resource(R.string.kv_error_title_required)), messages)
        assertEquals(before, store.namespaces.items.value.size)
    }

    @Test
    fun `a title longer than 512 characters is refused with the too-long message`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        val messages = recordMessages(vm)

        vm.create("t".repeat(LONG_TITLE_CHARS))
        advanceUntilIdle()

        assertEquals(listOf<UiText>(UiText.Resource(R.string.kv_error_title_too_long)), messages)
    }

    @Test
    fun `a created namespace is stored with the trimmed title and the list shows it`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        val messages = recordMessages(vm)

        vm.create("  Build cache  ", KvJurisdiction.EU)
        advanceUntilIdle()

        assertEquals(listOf<UiText>(UiText.Resource(R.string.kv_message_namespace_created, listOf("Build cache"))), messages)
        val content = vm.state.value.namespaces as UiState.Content
        assertTrue(content.data.any { it.title == "Build cache" })
        assertEquals(false, vm.state.value.isCreating)
    }

    @Test
    fun `a failed create reports the failure and leaves the list as it was`() = runTest(dispatcher) {
        val failing = KvRepository(
            mockCloudflareApi {
                post("/accounts/{accountId}/storage/kv/namespaces") {
                    DemoReply.failure(HttpStatusCode.InternalServerError, CREATE_FAILURE_CODE, "unavailable")
                }
            },
        )
        val vm = viewModel(failing)
        advanceUntilIdle()
        val messages = recordMessages(vm)

        vm.create("Will fail")
        advanceUntilIdle()

        assertEquals(1, messages.size)
        assertEquals(false, vm.state.value.isCreating)
        assertEquals(false, store.namespaces.items.value.values.any { it.title == "Will fail" })
    }
}
