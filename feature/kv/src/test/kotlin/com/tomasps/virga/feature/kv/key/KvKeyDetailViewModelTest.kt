package com.tomasps.virga.feature.kv.key

import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.core.ui.state.UiText
import com.tomasps.virga.feature.kv.R
import com.tomasps.virga.feature.kv.changes.KvNamespaceChanges
import com.tomasps.virga.feature.kv.data.KvRepository
import com.tomasps.virga.feature.kv.demo.KV_DEMO_ACCOUNT_PERSONAL
import com.tomasps.virga.feature.kv.demo.KV_DEMO_NS_FLAGS
import com.tomasps.virga.feature.kv.demo.KvDemoEntry
import com.tomasps.virga.feature.kv.demo.KvDemoRoutes
import com.tomasps.virga.feature.kv.demo.KvDemoStore
import com.tomasps.virga.feature.kv.testing.FakeKvFileStore
import com.tomasps.virga.feature.kv.testing.MainDispatcherRule
import com.tomasps.virga.feature.kv.testing.TEST_NOW_EPOCH_SECONDS
import com.tomasps.virga.feature.kv.testing.WAIT_MS
import com.tomasps.virga.feature.kv.testing.awaitTrue
import com.tomasps.virga.feature.kv.testing.signedInSession
import com.tomasps.virga.feature.kv.value.KvExpirationDraft
import com.tomasps.virga.feature.kv.value.KvExpirationMode
import com.tomasps.virga.feature.kv.value.KvValueView
import com.tomasps.virga.feature.kv.value.KvWriteThrottle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.ZoneId

private const val NS = KV_DEMO_NS_FLAGS
private const val KEY = "notes/today"
private const val ONE_HOUR = 3_600L
private const val TOO_SOON = 30L
private const val CLOCK_MILLIS = TEST_NOW_EPOCH_SECONDS * 1_000L

class KvKeyDetailViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val store = KvDemoStore(TEST_NOW_EPOCH_SECONDS)
    private val repository = KvRepository(
        mockCloudflareApi { KvDemoRoutes(store) { TEST_NOW_EPOCH_SECONDS }.contribute(this) },
    )

    private fun viewModel(): KvKeyDetailViewModel = KvKeyDetailViewModel(
        namespaceId = NS,
        keyName = KEY,
        session = signedInSession(KV_DEMO_ACCOUNT_PERSONAL),
        repository = repository,
        writer = KvKeyWriter(repository, KvWriteThrottle { CLOCK_MILLIS }) { TEST_NOW_EPOCH_SECONDS },
        changes = KvNamespaceChanges(),
        files = FakeKvFileStore(),
        zone = ZoneId.of("UTC"),
        nowEpochSeconds = { TEST_NOW_EPOCH_SECONDS },
    )

    private fun seed(bytes: ByteArray, metadata: JsonElement?, expiresAt: Long?) {
        store.entries.put(store.entryKey(KV_DEMO_ACCOUNT_PERSONAL, NS, KEY), KvDemoEntry(bytes, metadata, expiresAt))
    }

    private fun stored() = store.entries.get(store.entryKey(KV_DEMO_ACCOUNT_PERSONAL, NS, KEY))

    private fun owner(name: String) = buildJsonObject { put("owner", name) }

    /** Runs [body] with a recorder on the events flow. The recorder is cancelled even when an assertion fails. */
    private fun withEvents(vm: KvKeyDetailViewModel, body: suspend (List<KvKeyEvent>) -> Unit) = runBlocking {
        val received = mutableListOf<KvKeyEvent>()
        val recorder = launch(Dispatchers.Unconfined) { vm.events.collectInto(received) }
        try {
            body(received)
        } finally {
            recorder.cancel()
        }
    }

    @Test
    fun `editing a text value keeps its metadata and its expiration`() = runBlocking {
        // Arrange
        seed("old text".encodeToByteArray(), owner("team"), TEST_NOW_EPOCH_SECONDS + ONE_HOUR)
        val vm = viewModel()
        awaitTrue { vm.state.value.details is UiState.Content }
        vm.showValue()
        awaitTrue { vm.state.value.canEditText }

        // Act
        vm.startEdit()
        vm.updateEditText("new text")
        vm.saveEdit()
        awaitTrue { vm.state.value.editText == null && !vm.state.value.isBusy }

        // Assert
        assertEquals("new text", String(stored()!!.bytes))
        assertEquals(owner("team"), stored()!!.metadata)
        assertEquals(TEST_NOW_EPOCH_SECONDS + ONE_HOUR, stored()!!.expiresAtEpochSeconds)
    }

    @Test
    fun `an edit is refused with a message and no write when the key expires within a minute`() {
        // Arrange
        seed("kept".encodeToByteArray(), null, TEST_NOW_EPOCH_SECONDS + TOO_SOON)
        val vm = viewModel()
        withEvents(vm) { events ->
            awaitTrue { vm.state.value.details is UiState.Content }
            vm.showValue()
            awaitTrue { vm.state.value.canEditText }

            // Act
            vm.startEdit()
            vm.updateEditText("changed")
            vm.saveEdit()
            awaitTrue { events.any { it is KvKeyEvent.Message } }

            // Assert
            assertEquals("kept", String(stored()!!.bytes))
            val message = (events.filterIsInstance<KvKeyEvent.Message>().single().text) as UiText.Resource
            assertEquals(R.string.kv_error_expires_too_soon, message.id)
        }
    }

    @Test
    fun `editing metadata rewrites the value with the new metadata and keeps the bytes`() = runBlocking {
        // Arrange
        seed("body".encodeToByteArray(), owner("old"), null)
        val vm = viewModel()
        awaitTrue { vm.state.value.details is UiState.Content }

        // Act
        vm.openMetadataEditor()
        vm.updateMetadataText("""{"owner":"new"}""")
        vm.saveMetadata()
        awaitTrue { vm.state.value.metadataText == null && !vm.state.value.isBusy }

        // Assert
        assertEquals(owner("new"), stored()!!.metadata)
        assertEquals("body", String(stored()!!.bytes))
        assertNull(stored()!!.expiresAtEpochSeconds)
    }

    @Test
    fun `a metadata save writes the value as it is now, not a copy this screen read earlier`() = runBlocking {
        // Arrange
        seed("first".encodeToByteArray(), owner("old"), null)
        val vm = viewModel()
        awaitTrue { vm.state.value.details is UiState.Content }
        vm.showValue()
        awaitTrue { vm.state.value.value is UiState.Content }
        seed("second".encodeToByteArray(), owner("old"), null) // changed elsewhere after this screen read it

        // Act
        vm.openMetadataEditor()
        vm.updateMetadataText("""{"owner":"new"}""")
        vm.saveMetadata()
        awaitTrue { vm.state.value.metadataText == null && !vm.state.value.isBusy }

        // Assert
        assertEquals("second", String(stored()!!.bytes))
        assertEquals(owner("new"), stored()!!.metadata)
    }

    @Test
    fun `choosing no expiration removes the expiration on save`() = runBlocking {
        // Arrange
        seed("body".encodeToByteArray(), null, TEST_NOW_EPOCH_SECONDS + ONE_HOUR)
        val vm = viewModel()
        awaitTrue { vm.state.value.details is UiState.Content }

        // Act
        vm.openExpirationEditor()
        vm.updateExpirationDraft(KvExpirationDraft(KvExpirationMode.NONE, ""))
        vm.saveExpiration()
        awaitTrue { vm.state.value.expirationDraft == null && !vm.state.value.isBusy }

        // Assert
        assertNull(stored()!!.expiresAtEpochSeconds)
        assertEquals("body", String(stored()!!.bytes))
    }

    @Test
    fun `a ttl shorter than 60 seconds is refused before any write`() {
        // Arrange
        seed("body".encodeToByteArray(), null, null)
        val vm = viewModel()
        withEvents(vm) { events ->
            awaitTrue { vm.state.value.details is UiState.Content }

            // Act
            vm.openExpirationEditor()
            vm.updateExpirationDraft(KvExpirationDraft(KvExpirationMode.TTL, "30"))
            vm.saveExpiration()
            awaitTrue { events.any { it is KvKeyEvent.Message } }

            // Assert
            assertNull(stored()!!.expiresAtEpochSeconds)
            val message = events.filterIsInstance<KvKeyEvent.Message>().single().text as UiText.Resource
            assertEquals(R.string.kv_error_expiration_ttl_short, message.id)
        }
    }

    @Test
    fun `binary values offer no text edit and show as a hex preview`() = runBlocking {
        // Arrange
        seed(byteArrayOf(0xFF.toByte(), 0x00, 0x01), null, null)
        val vm = viewModel()
        awaitTrue { vm.state.value.details is UiState.Content }

        // Act
        vm.showValue()
        awaitTrue { vm.state.value.value is UiState.Content }

        // Assert
        val view = (vm.state.value.value as UiState.Content).data
        assertTrue(view is KvValueView.Binary)
        assertTrue(!vm.state.value.canEditText)
    }

    @Test
    fun `delete removes the key and tells the screen it closed`() {
        // Arrange
        seed("body".encodeToByteArray(), null, null)
        val vm = viewModel()
        withEvents(vm) { events ->
            awaitTrue { vm.state.value.details is UiState.Content }

            // Act
            vm.requestDelete()
            vm.delete()
            awaitTrue { events.contains(KvKeyEvent.Closed) }

            // Assert
            assertNull(stored())
        }
    }

    @Test
    fun `a key that does not exist shows as missing`() = runBlocking {
        // Arrange and Act
        val vm = viewModel()
        awaitTrue { vm.state.value.isMissing }

        // Assert
        assertTrue(vm.state.value.isMissing)
    }

    @Test
    fun `the suggested file name keeps slashes out of the name`() {
        // Act and Assert
        assertEquals("users_42_profile", suggestedFileName("users/42/profile"))
    }
}

private suspend fun <E> SharedFlow<E>.collectInto(target: MutableList<E>) {
    collect { target += it }
}
