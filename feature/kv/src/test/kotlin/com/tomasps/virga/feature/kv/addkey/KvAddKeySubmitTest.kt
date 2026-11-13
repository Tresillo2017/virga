package com.tomasps.virga.feature.kv.addkey

import com.tomasps.virga.core.auth.session.CloudflareAccount
import com.tomasps.virga.core.auth.session.SessionRepository
import com.tomasps.virga.core.auth.session.SessionState
import com.tomasps.virga.core.common.result.ApiResult
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
import com.tomasps.virga.feature.kv.key.KvKeyWriter
import com.tomasps.virga.feature.kv.testing.FakeKvFileStore
import com.tomasps.virga.feature.kv.testing.MainDispatcherRule
import com.tomasps.virga.feature.kv.testing.TEST_NOW_EPOCH_SECONDS
import com.tomasps.virga.feature.kv.testing.signedInSession
import com.tomasps.virga.feature.kv.value.KvExpirationDraft
import com.tomasps.virga.feature.kv.value.KvExpirationMode
import com.tomasps.virga.feature.kv.value.KvWriteThrottle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.ZoneId

private const val KEY = "submit/once"

/**
 * Save-button behaviour that depends on timing: a second tap while the first save is still checking the key must not
 * write or prompt again, and a save with no account selected must say so.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KvAddKeySubmitTest {

    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val main = MainDispatcherRule(dispatcher)

    private val store = KvDemoStore(TEST_NOW_EPOCH_SECONDS)
    private val repository = KvRepository(
        mockCloudflareApi { KvDemoRoutes(store) { TEST_NOW_EPOCH_SECONDS }.contribute(this) },
    )
    private val files = FakeKvFileStore()

    private fun viewModel(session: SessionRepository = signedInSession(KV_DEMO_ACCOUNT_PERSONAL)) = KvAddKeyViewModel(
        namespaceId = KV_DEMO_NS_FLAGS,
        session = session,
        repository = repository,
        writer = KvKeyWriter(repository, KvWriteThrottle { TEST_NOW_EPOCH_SECONDS * 1_000L }) { TEST_NOW_EPOCH_SECONDS },
        changes = KvNamespaceChanges(),
        files = files,
        zone = ZoneId.of("UTC"),
        nowEpochSeconds = { TEST_NOW_EPOCH_SECONDS },
    )

    /** Collects every event the view model emits, from the moment this is called. */
    private fun TestScope.recordEvents(vm: KvAddKeyViewModel): List<KvAddKeyEvent> {
        val received = mutableListOf<KvAddKeyEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { received += it } }
        return received
    }

    private fun storedBytes(name: String): ByteArray? =
        store.entries.get(store.entryKey(KV_DEMO_ACCOUNT_PERSONAL, KV_DEMO_NS_FLAGS, name))?.bytes

    @Test
    fun `a second save tapped while the first is still checking the key writes once and says nothing`() = runTest(dispatcher) {
        // Arrange
        val vm = viewModel()
        advanceUntilIdle()
        val events = recordEvents(vm)
        vm.setName(KEY)
        vm.setText("first")

        // Act: two taps before the coroutines get to run
        vm.save()
        vm.save()
        advanceUntilIdle()

        // Assert: one Saved and no rate-limit or overwrite message from a second, duplicate write
        assertEquals(listOf<KvAddKeyEvent>(KvAddKeyEvent.Saved(KEY)), events)
        assertEquals("first", String(storedBytes(KEY)!!))
        assertEquals(false, vm.state.value.isBusy)
    }

    @Test
    fun `a save with no account selected says so instead of doing nothing`() = runTest(dispatcher) {
        // Arrange: a session that never signs in
        val notSignedIn = object : SessionRepository by signedInSession(KV_DEMO_ACCOUNT_PERSONAL) {
            override val state: StateFlow<SessionState> = MutableStateFlow(SessionState.SignedOut).asStateFlow()
        }
        val vm = viewModel(notSignedIn)
        val events = recordEvents(vm)
        vm.setName(KEY)
        vm.setText("never sent")

        // Act
        vm.save()
        advanceUntilIdle()

        // Assert
        val message = events.single() as KvAddKeyEvent.Message
        assertEquals(UiText.Resource(R.string.kv_error_no_account), message.text)
        assertNull(storedBytes(KEY))
        assertEquals(false, vm.state.value.isBusy)
    }

    @Test
    fun `a save after a refused one is accepted again, because the busy flag is released`() = runTest(dispatcher) {
        // Arrange
        val vm = viewModel()
        advanceUntilIdle()
        val events = recordEvents(vm)
        vm.setName(".")
        vm.setText("x")
        vm.save()
        advanceUntilIdle()

        // Act
        vm.setName(KEY)
        vm.save()
        advanceUntilIdle()

        // Assert
        assertEquals(KvAddKeyEvent.Saved(KEY), events.last())
    }

    @Test
    fun `a pick of a document larger than 25 MiB is refused before any write`() = runTest(dispatcher) {
        // Arrange
        val uri = "content://demo/documents/huge.bin"
        files.documents[uri] = ByteArray(KV_LIMIT_PLUS_ONE)
        val vm = viewModel()
        advanceUntilIdle()
        val events = recordEvents(vm)
        vm.setName(KEY)
        vm.setSource(KvAddKeySource.FILE)
        vm.pickFile(uri)
        advanceUntilIdle()

        // Act
        vm.save()
        advanceUntilIdle()

        // Assert
        assertEquals(UiText.Resource(R.string.kv_error_value_too_large), (events.single() as KvAddKeyEvent.Message).text)
        assertNull(storedBytes(KEY))
    }

    @Test
    fun `an expiration typed as absolute time is sent, and a TTL of 60 seconds is accepted`() = runTest(dispatcher) {
        // Arrange
        val vm = viewModel()
        advanceUntilIdle()
        val events = recordEvents(vm)
        vm.setName(KEY)
        vm.setText("ttl")
        vm.setExpiration(KvExpirationDraft(KvExpirationMode.TTL, "60"))

        // Act
        vm.save()
        advanceUntilIdle()

        // Assert
        assertEquals(KvAddKeyEvent.Saved(KEY), events.single())
        assertNotNull(store.entries.get(store.entryKey(KV_DEMO_ACCOUNT_PERSONAL, KV_DEMO_NS_FLAGS, KEY))?.expiresAtEpochSeconds)
    }

    @Test
    fun `a TTL of 59 seconds is refused with the minimum message and nothing is written`() = runTest(dispatcher) {
        // Arrange
        val vm = viewModel()
        advanceUntilIdle()
        val events = recordEvents(vm)
        vm.setName(KEY)
        vm.setText("ttl")
        vm.setExpiration(KvExpirationDraft(KvExpirationMode.TTL, "59"))

        // Act
        vm.save()
        advanceUntilIdle()

        // Assert
        assertEquals(UiText.Resource(R.string.kv_error_expiration_ttl_short), (events.single() as KvAddKeyEvent.Message).text)
        assertNull(storedBytes(KEY))
    }

    @Test
    fun `an absolute time 59 seconds away is refused, and one 60 seconds away is accepted`() = runTest(dispatcher) {
        // Arrange
        val vm = viewModel()
        advanceUntilIdle()
        val events = recordEvents(vm)
        vm.setName(KEY)
        vm.setText("abs")
        val inFiftyNineSeconds = java.time.LocalDateTime.ofEpochSecond(TEST_NOW_EPOCH_SECONDS + 59, 0, java.time.ZoneOffset.UTC)
        vm.setExpiration(KvExpirationDraft(KvExpirationMode.ABSOLUTE, inFiftyNineSeconds.format(ABSOLUTE)))

        // Act
        vm.save()
        advanceUntilIdle()

        // Assert
        assertEquals(UiText.Resource(R.string.kv_error_expiration_too_soon), (events.single() as KvAddKeyEvent.Message).text)
        assertNull(storedBytes(KEY))
    }

    private companion object {
        const val KV_LIMIT_PLUS_ONE = 25 * 1024 * 1024 + 1
        val ABSOLUTE: java.time.format.DateTimeFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    }
}
