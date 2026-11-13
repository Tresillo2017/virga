package com.tomasps.virga.feature.kv.addkey

import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.core.ui.state.UiText
import com.tomasps.virga.feature.kv.R
import com.tomasps.virga.feature.kv.changes.KvNamespaceChanges
import com.tomasps.virga.feature.kv.data.KvRepository
import com.tomasps.virga.feature.kv.demo.KV_DEMO_ACCOUNT_PERSONAL
import com.tomasps.virga.feature.kv.demo.KV_DEMO_NS_FLAGS
import com.tomasps.virga.feature.kv.demo.KvDemoEntry
import com.tomasps.virga.feature.kv.demo.KvDemoRoutes
import com.tomasps.virga.feature.kv.demo.KvDemoStore
import com.tomasps.virga.feature.kv.key.KvKeyWriter
import com.tomasps.virga.feature.kv.testing.FakeKvFileStore
import com.tomasps.virga.feature.kv.testing.MainDispatcherRule
import com.tomasps.virga.feature.kv.testing.TEST_NOW_EPOCH_SECONDS
import com.tomasps.virga.feature.kv.testing.awaitTrue
import com.tomasps.virga.feature.kv.testing.signedInSession
import com.tomasps.virga.feature.kv.value.KvExpirationDraft
import com.tomasps.virga.feature.kv.value.KvExpirationMode
import com.tomasps.virga.feature.kv.value.KvWriteThrottle
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.ZoneId

private const val EXISTING = "existing/key"
private const val NEW_KEY = "fresh/key"
private const val FILE_URI = "content://demo/documents/report.txt"
private const val LOOKUP_FAILURE_CODE = 10_000

class KvAddKeyViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val store = KvDemoStore(TEST_NOW_EPOCH_SECONDS)
    private val repository = KvRepository(
        mockCloudflareApi { KvDemoRoutes(store) { TEST_NOW_EPOCH_SECONDS }.contribute(this) },
    )
    private val files = FakeKvFileStore()

    private fun viewModel() = KvAddKeyViewModel(
        namespaceId = KV_DEMO_NS_FLAGS,
        session = signedInSession(KV_DEMO_ACCOUNT_PERSONAL),
        repository = repository,
        writer = KvKeyWriter(repository, KvWriteThrottle()) { TEST_NOW_EPOCH_SECONDS },
        changes = KvNamespaceChanges(),
        files = files,
        zone = ZoneId.of("UTC"),
        nowEpochSeconds = { TEST_NOW_EPOCH_SECONDS },
    )

    private fun storedBytes(name: String): ByteArray? =
        store.entries.get(store.entryKey(KV_DEMO_ACCOUNT_PERSONAL, KV_DEMO_NS_FLAGS, name))?.bytes

    /** Runs [body] with a recorder on the events flow. The recorder is cancelled even when an assertion fails. */
    private fun withEvents(vm: KvAddKeyViewModel, body: suspend (List<KvAddKeyEvent>) -> Unit) = runBlocking {
        val received = mutableListOf<KvAddKeyEvent>()
        val recorder = launch(Dispatchers.Unconfined) { vm.events.collect { received += it } }
        try {
            body(received)
        } finally {
            recorder.cancel()
        }
    }

    @Test
    fun `the dot name is refused with its own message and nothing is written`() {
        // Arrange
        val vm = viewModel()
        withEvents(vm) { events ->
            vm.setName(".")
            vm.setText("x")

            // Act
            vm.save()
            awaitTrue { events.any { it is KvAddKeyEvent.Message } }

            // Assert
            val message = events.filterIsInstance<KvAddKeyEvent.Message>().single().text as UiText.Resource
            assertEquals(R.string.kv_error_key_dot_segment, message.id)
            assertNull(storedBytes("."))
        }
    }

    @Test
    fun `replacing an existing key asks first and writes only after confirmation`() = runBlocking {
        // Arrange
        store.entries.put(
            store.entryKey(KV_DEMO_ACCOUNT_PERSONAL, KV_DEMO_NS_FLAGS, EXISTING),
            KvDemoEntry("old".encodeToByteArray(), null, null),
        )
        val vm = viewModel()
        vm.setName(EXISTING)
        vm.setText("new")

        // Act
        vm.save()
        awaitTrue { vm.state.value.showOverwrite }
        val beforeConfirm = String(storedBytes(EXISTING)!!)
        vm.confirmOverwrite()
        awaitTrue { storedBytes(EXISTING)?.let { String(it) } == "new" }

        // Assert
        assertEquals("old", beforeConfirm)
    }

    @Test
    fun `a picked document is written under the typed name`() = runBlocking {
        // Arrange
        files.documents[FILE_URI] = "from a file".encodeToByteArray()
        val vm = viewModel()
        vm.setName(NEW_KEY)
        vm.setSource(KvAddKeySource.FILE)
        vm.pickFile(FILE_URI)
        awaitTrue { vm.state.value.fileUri != null }

        // Act
        vm.save()
        awaitTrue { storedBytes(NEW_KEY) != null }

        // Assert
        assertEquals("from a file", String(storedBytes(NEW_KEY)!!))
    }

    @Test
    fun `when the existence check fails nothing is written and the user is told why`() = runBlocking {
        // Arrange
        var writes = 0
        val brokenLookup = KvRepository(
            mockCloudflareApi {
                get("/accounts/{accountId}/storage/kv/namespaces/{namespaceId}/keys") {
                    DemoReply.failure(HttpStatusCode.InternalServerError, LOOKUP_FAILURE_CODE, "unavailable")
                }
                put("/accounts/{accountId}/storage/kv/namespaces/{namespaceId}/values/{key...}") {
                    writes++
                    DemoReply.ok(JsonNull)
                }
            },
        )
        val vm = KvAddKeyViewModel(
            namespaceId = KV_DEMO_NS_FLAGS,
            session = signedInSession(KV_DEMO_ACCOUNT_PERSONAL),
            repository = brokenLookup,
            writer = KvKeyWriter(brokenLookup, KvWriteThrottle()) { TEST_NOW_EPOCH_SECONDS },
            changes = KvNamespaceChanges(),
            files = files,
            zone = ZoneId.of("UTC"),
            nowEpochSeconds = { TEST_NOW_EPOCH_SECONDS },
        )
        vm.setName(EXISTING)
        vm.setText("new")

        withEvents(vm) { events ->
            // Act
            vm.save()
            awaitTrue { events.isNotEmpty() }

            // Assert
            assertEquals(0, writes)
            assertFalse(vm.state.value.showOverwrite)
            val text = (events.single() as KvAddKeyEvent.Message).text as UiText.Resource
            assertEquals(R.string.kv_error_overwrite_check_failed, text.id)
        }
    }

    @Test
    fun `an expiration under 60 seconds is refused before the write`() {
        // Arrange
        val vm = viewModel()
        withEvents(vm) { events ->
            vm.setName(NEW_KEY)
            vm.setText("x")
            vm.setExpiration(KvExpirationDraft(KvExpirationMode.TTL, "5"))

            // Act
            vm.save()
            awaitTrue { events.any { it is KvAddKeyEvent.Message } }

            // Assert
            assertNull(storedBytes(NEW_KEY))
            val message = events.filterIsInstance<KvAddKeyEvent.Message>().single().text as UiText.Resource
            assertEquals(R.string.kv_error_expiration_ttl_short, message.id)
        }
    }
}
