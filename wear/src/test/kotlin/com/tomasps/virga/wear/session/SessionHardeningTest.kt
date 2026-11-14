package com.tomasps.virga.wear.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SessionHardeningTest {

    @get:Rule
    val folder = TemporaryFolder()

    private class PlainCipher : SecretCipher {
        override fun encrypt(plaintext: ByteArray) = SealedBytes(ByteArray(1), plaintext)
        override fun decrypt(sealed: SealedBytes): ByteArray = sealed.ciphertext
    }

    /** A DataStore whose writes can be made to fail, by call number or all at once. */
    private class FailingStore(private val delegate: DataStore<Preferences>) : DataStore<Preferences> {
        var calls = 0
        var failOnCall = -1
        var failAll = false
        override val data: Flow<Preferences> = delegate.data
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            calls++
            if (failAll || calls == failOnCall) throw IOException("disk")
            return delegate.updateData(transform)
        }
    }

    private inner class Harness(testScope: TestScope) {
        val dispatcher = UnconfinedTestDispatcher(testScope.testScheduler)
        val scope = CoroutineScope(Job() + dispatcher)
        val vaultStore = FailingStore(PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "v.preferences_pb") })
        val prefsStore = FailingStore(PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "p.preferences_pb") })
        val vault = CredentialVault(
            vaultStore,
            PlainCipher(),
            object : DispatcherProvider {
                override val io: CoroutineDispatcher = dispatcher
                override val default: CoroutineDispatcher = dispatcher
            },
        )
        val prefs = WatchSessionPrefs(prefsStore)
        val demoMode = DemoMode()
        val active = ActiveCredential()
        var now = START
        val repo = WatchSessionRepository(vault, active, demoMode, prefs) { now }

        /** A fresh repository over the same storage, as after a process restart. */
        fun restarted() = WatchSessionRepository(vault, ActiveCredential(), DemoMode(), prefs) { now }

        fun stop() = scope.coroutineContext[Job]!!.cancel()
    }

    private fun payload(
        token: String? = "tok",
        isDemo: Boolean = false,
        selected: String? = "a1",
        accounts: List<HandoffAccount> = listOf(HandoffAccount("a1", "One"), HandoffAccount("a2", "Two")),
    ) = HandoffPayload(HandoffPayload.CURRENT_VERSION, token, isDemo, accounts, selected, sentAtMillis = 5L)

    @Test
    fun prefsFailureAfterVaultWriteNeverRestoresAsConnectedWithOldAccount() = runTest {
        val h = Harness(this)
        h.repo.apply(payload(token = "old", selected = "a1"))

        h.prefsStore.calls = 0
        h.prefsStore.failOnCall = 2 // call 1 clears the check-in, call 2 is the metadata write
        val result = runCatching { h.repo.apply(payload(token = "new", selected = "a2", accounts = listOf(HandoffAccount("a2", "Two")))) }
        assertTrue(result.isFailure)
        assertFalse(h.repo.state.value is WatchSessionState.Connected)
        assertNull(h.active.bearerToken())

        val restarted = h.restarted()
        restarted.restore()
        assertFalse(restarted.state.value is WatchSessionState.Connected)
        h.stop()
    }

    @Test
    fun vaultWipeFailureStillClearsPrefsSoRestartIsNotConnected() = runTest {
        val h = Harness(this)
        h.repo.apply(payload())
        h.vaultStore.failAll = true

        assertTrue(runCatching { h.repo.clear() }.isFailure)
        assertEquals(WatchSessionState.NotConnected, h.repo.state.value)
        assertNull(h.active.bearerToken())
        assertNull(h.prefs.lastCheckInMillis())

        val restarted = h.restarted()
        restarted.restore()
        assertFalse(restarted.state.value is WatchSessionState.Connected)
        h.stop()
    }

    @Test
    fun unauthorizedWithFailingVaultWipeStillDisconnects() = runTest {
        val h = Harness(this)
        h.repo.apply(payload())
        h.vaultStore.failAll = true

        runCatching { h.repo.onUnauthorized(h.repo.issuedCredential()!!.generation) }
        assertEquals(WatchSessionState.NotConnected, h.repo.state.value)
        val restarted = h.restarted()
        restarted.restore()
        assertFalse(restarted.state.value is WatchSessionState.Connected)
        h.stop()
    }

    @Test
    fun unauthorizedClearsPrefs() = runTest {
        val h = Harness(this)
        h.repo.apply(payload())
        h.repo.onUnauthorized(h.repo.issuedCredential()!!.generation)

        assertNull(h.prefs.lastCheckInMillis())
        assertNull(h.prefs.selectedAccountId())
        assertTrue(h.prefs.accountIds().isEmpty())
        h.stop()
    }

    @Test
    fun livePayloadAfterDemoTurnsDemoModeOff() = runTest {
        val h = Harness(this)
        h.repo.apply(payload(token = null, isDemo = true))
        assertTrue(h.demoMode.enabled.value)

        h.repo.apply(payload(token = "tok"))
        assertFalse(h.demoMode.enabled.value)
        assertEquals("tok", h.active.bearerToken())
        h.stop()
    }

    @Test
    fun providerServesTokenAtExactly24hAndNotAfterWhileProcessLives() = runTest {
        val h = Harness(this)
        val provider = StalenessGuardedCredentialProvider(h.repo)
        h.repo.apply(payload())

        h.now = START + StalenessPolicy.MAX_OFFLINE_MILLIS
        assertEquals("tok", provider.bearerToken())

        h.now = START + StalenessPolicy.MAX_OFFLINE_MILLIS + 1
        assertNull(provider.bearerToken())
        assertEquals(WatchSessionState.Stale, h.repo.state.value)
        assertNull(h.active.bearerToken())

        h.repo.apply(payload())
        assertEquals("tok", provider.bearerToken())
        h.stop()
    }

    @Test
    fun providerRecordsTheGenerationItSentAndNothingWhenItSendsNothing() = runTest {
        val h = Harness(this)
        val provider = StalenessGuardedCredentialProvider(h.repo)
        val guard = SessionSentCredentialGuard(h.repo)
        assertNull(guard.track { provider.bearerToken() }.sentGeneration)

        h.repo.apply(payload())
        val sent = guard.track { provider.bearerToken() }
        assertEquals("tok", sent.value)
        assertEquals(h.repo.issuedCredential()!!.generation, sent.sentGeneration)

        h.now = START + StalenessPolicy.MAX_OFFLINE_MILLIS + 1
        val stale = guard.track { provider.bearerToken() }
        assertNull(stale.value)
        assertNull("a stale session sends nothing, so nothing can be blamed for a 401", stale.sentGeneration)
        h.stop()
    }

    @Test
    fun changedThenDeletedEndsNotConnectedWithEmptyVault() = runTest {
        val h = Harness(this)
        val handler = HandoffEventHandler(h.repo) { }
        handler.handle(
            listOf(
                HandoffEvent.Changed(HandoffCodec.encode(payload())),
                HandoffEvent.Deleted,
            ),
        )

        assertEquals(WatchSessionState.NotConnected, h.repo.state.value)
        assertNull(h.vault.read())
        h.stop()
    }

    @Test
    fun deletedThenChangedEndsConnected() = runTest {
        val h = Harness(this)
        val handler = HandoffEventHandler(h.repo) { }
        handler.handle(listOf(HandoffEvent.Deleted, HandoffEvent.Changed(HandoffCodec.encode(payload()))))

        assertEquals(WatchSessionState.Connected("a1", isDemo = false), h.repo.state.value)
        h.stop()
    }

    @Test
    fun failingEventIsReportedAndLaterEventsStillRun() = runTest {
        val h = Harness(this)
        h.repo.apply(payload())
        h.prefsStore.calls = 0
        h.prefsStore.failOnCall = 1 // only the update's first write fails; the delete must still work
        val errors = mutableListOf<Throwable>()
        val handler = HandoffEventHandler(h.repo) { errors += it }

        handler.handle(listOf(HandoffEvent.Changed(HandoffCodec.encode(payload(token = "x"))), HandoffEvent.Deleted))

        assertEquals(1, errors.size)
        assertEquals(WatchSessionState.NotConnected, h.repo.state.value)
        h.stop()
    }

    private companion object {
        const val START = 1_000_000L
    }
}
