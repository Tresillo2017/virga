package com.tomasps.virga.wear.session

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.tomasps.virga.core.auth.Credential
import com.tomasps.virga.core.auth.session.ActiveCredential
import com.tomasps.virga.core.auth.vault.CredentialVault
import com.tomasps.virga.core.auth.vault.SealedBytes
import com.tomasps.virga.core.auth.vault.SecretCipher
import com.tomasps.virga.core.common.DispatcherProvider
import com.tomasps.virga.core.network.demo.DemoMode
import com.tomasps.virga.core.wearsync.HandoffAccount
import com.tomasps.virga.core.wearsync.HandoffCodec
import com.tomasps.virga.core.wearsync.HandoffPayload
import com.tomasps.virga.core.wearsync.StalenessPolicy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class PhoneItemBootstrapTest {

    @get:Rule
    val folder = TemporaryFolder()

    private class PlainCipher : SecretCipher {
        override fun encrypt(plaintext: ByteArray) = SealedBytes(ByteArray(1), plaintext)
        override fun decrypt(sealed: SealedBytes): ByteArray = sealed.ciphertext
    }

    /** The phone's item as the Data Layer holds it; [onRead] runs during the read. */
    private class FakeItem(var bytes: ByteArray? = null) : CredentialItemSource {
        var reads = 0
        var failure: Exception? = null
        var onRead: suspend () -> Unit = {}
        override suspend fun current(): ByteArray? {
            reads++
            failure?.let { throw it }
            onRead()
            return bytes
        }
    }

    private inner class Harness(testScope: TestScope) {
        private val dispatcher = UnconfinedTestDispatcher(testScope.testScheduler)
        private val scope = CoroutineScope(Job() + dispatcher)
        val vault = CredentialVault(
            PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "v.preferences_pb") },
            PlainCipher(),
            object : DispatcherProvider {
                override val io: CoroutineDispatcher = dispatcher
                override val default: CoroutineDispatcher = dispatcher
            },
        )
        val prefs = WatchSessionPrefs(PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "p.preferences_pb") })
        var now = START
        val repo = WatchSessionRepository(vault, ActiveCredential(), DemoMode(), prefs) { now }
        val item = FakeItem()
        val warnings = mutableListOf<String>()
        val bootstrap = PhoneItemBootstrap(repo, item) { warnings += it }

        fun stop() = scope.coroutineContext[Job]!!.cancel()
    }

    private fun payload(token: String = "tok", sentAt: Long = 5L) = HandoffPayload(
        HandoffPayload.CURRENT_VERSION, token, false, listOf(HandoffAccount("a1", "One")), "a1", sentAtMillis = sentAt,
    )

    private fun encoded(token: String = "tok", sentAt: Long = 5L) = HandoffCodec.encode(payload(token, sentAt))

    private val connected = WatchSessionState.Connected("a1", isDemo = false)

    @Test
    fun anUnseenItemConnectsAFreshWatch() = runTest {
        val h = Harness(this)
        h.repo.restore()
        h.item.bytes = encoded()

        h.bootstrap.syncFromPhone()

        assertEquals(connected, h.repo.state.value)
        assertEquals(Credential.ApiToken("tok"), h.vault.read())
        h.stop()
    }

    @Test
    fun theSameItemAfterTheStaleLockStaysStale() = runTest {
        val h = Harness(this)
        h.repo.applyEncoded(encoded())
        h.now = START + StalenessPolicy.MAX_OFFLINE_MILLIS + 1
        h.repo.refreshStaleness()
        h.item.bytes = encoded()

        h.bootstrap.syncFromPhone()

        assertEquals(WatchSessionState.Stale, h.repo.state.value)
        h.stop()
    }

    @Test
    fun aNewerItemAfterTheStaleLockReconnects() = runTest {
        val h = Harness(this)
        h.repo.applyEncoded(encoded(sentAt = 5L))
        h.now = START + StalenessPolicy.MAX_OFFLINE_MILLIS + 1
        h.repo.refreshStaleness()
        h.item.bytes = encoded(sentAt = 9L)

        h.bootstrap.syncFromPhone()

        assertEquals(connected, h.repo.state.value)
        h.stop()
    }

    @Test
    fun theSameItemAfterDisconnectStaysNotConnected() = runTest {
        val h = Harness(this)
        h.repo.applyEncoded(encoded())
        h.repo.clear()
        h.item.bytes = encoded()

        h.bootstrap.syncFromPhone()

        assertEquals(WatchSessionState.NotConnected, h.repo.state.value)
        assertNull(h.vault.read())
        h.stop()
    }

    @Test
    fun theSameItemAfterA401WipeStaysNotConnected() = runTest {
        val h = Harness(this)
        h.repo.applyEncoded(encoded())
        h.repo.onUnauthorized(h.repo.issuedCredential()!!.generation)
        h.item.bytes = encoded()

        h.bootstrap.syncFromPhone()

        assertEquals(WatchSessionState.NotConnected, h.repo.state.value)
        h.stop()
    }

    @Test
    fun anInvalidOrMissingItemChangesNothing() = runTest {
        val h = Harness(this)
        h.repo.restore()
        h.item.bytes = "not json".encodeToByteArray()
        h.bootstrap.syncFromPhone()
        h.item.bytes = null
        h.bootstrap.syncFromPhone()

        assertEquals(WatchSessionState.NotConnected, h.repo.state.value)
        assertEquals(2, h.item.reads)
        h.stop()
    }

    @Test
    fun aConnectedWatchDoesNotReadTheItem() = runTest {
        val h = Harness(this)
        h.repo.applyEncoded(encoded())
        h.item.bytes = encoded(token = "other", sentAt = 9L)

        h.bootstrap.syncFromPhone()

        assertEquals(0, h.item.reads)
        assertEquals(Credential.ApiToken("tok"), h.vault.read())
        h.stop()
    }

    @Test
    fun aSessionChangeDuringTheReadWins() = runTest {
        val h = Harness(this)
        h.repo.restore()
        h.item.bytes = encoded(token = "old")
        // A live sign-out event lands between the read and the apply.
        h.item.onRead = { h.repo.clear() }

        h.bootstrap.syncFromPhone()

        assertEquals(WatchSessionState.NotConnected, h.repo.state.value)
        assertNull(h.vault.read())
        h.stop()
    }

    @Test
    fun aFailedReadChangesNothingAndLogsTheClassOnly() = runTest {
        val h = Harness(this)
        h.repo.restore()
        h.item.failure = IOException("secret detail")

        h.bootstrap.syncFromPhone()

        assertEquals(WatchSessionState.NotConnected, h.repo.state.value)
        assertTrue(h.warnings.single().contains("IOException"))
        assertTrue(!h.warnings.single().contains("secret detail"))
        h.stop()
    }

    private companion object {
        const val START = 1_000_000L
    }
}
