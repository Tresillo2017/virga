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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class WatchSessionRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    /** Identity "encryption": the vault round-trips without the Keystore. */
    private class PlainCipher : SecretCipher {
        override fun encrypt(plaintext: ByteArray) = SealedBytes(ByteArray(1), plaintext)
        override fun decrypt(sealed: SealedBytes): ByteArray = sealed.ciphertext
    }

    private class Harness(
        val vault: CredentialVault,
        val active: ActiveCredential,
        val demoMode: DemoMode,
        val prefs: WatchSessionPrefs,
        val repo: WatchSessionRepository,
        val scope: CoroutineScope,
    ) {
        var now = START

        fun stop() = scope.coroutineContext[Job]!!.cancel()
    }

    private fun TestScope.harness(): Harness {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(Job() + dispatcher)
        val dispatchers = object : DispatcherProvider {
            override val io: CoroutineDispatcher = dispatcher
            override val default: CoroutineDispatcher = dispatcher
        }
        val vault = CredentialVault(
            PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "vault.preferences_pb") },
            PlainCipher(),
            dispatchers,
        )
        val prefs = WatchSessionPrefs(
            PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "prefs.preferences_pb") },
        )
        val active = ActiveCredential()
        val demoMode = DemoMode()
        val harness = arrayOfNulls<Harness>(1)
        val repo = WatchSessionRepository(vault, active, demoMode, prefs) { harness[0]!!.now }
        return Harness(vault, active, demoMode, prefs, repo, scope).also { harness[0] = it }
    }

    private fun payload(
        token: String? = "tok",
        isDemo: Boolean = false,
        selected: String? = "a1",
        accounts: List<HandoffAccount> = listOf(HandoffAccount("a1", "One"), HandoffAccount("a2", "Two")),
    ) = HandoffPayload(HandoffPayload.CURRENT_VERSION, token, isDemo, accounts, selected, sentAtMillis = 5L)

    @Test
    fun applyStoresTokenAndConnects() = runTest {
        val h = harness()
        h.repo.apply(payload())

        assertEquals(WatchSessionState.Connected("a1", isDemo = false), h.repo.state.value)
        assertEquals(Credential.ApiToken("tok"), h.vault.read())
        assertEquals("tok", h.active.bearerToken())
        assertFalse(h.demoMode.enabled.value)
        h.stop()
    }

    @Test
    fun applyDemoStoresDemoCredentialAndEnablesDemoMode() = runTest {
        val h = harness()
        h.repo.apply(payload(token = null, isDemo = true))

        assertEquals(WatchSessionState.Connected("a1", isDemo = true), h.repo.state.value)
        assertEquals(Credential.Demo, h.vault.read())
        assertNull(h.active.bearerToken())
        assertTrue(h.demoMode.enabled.value)
        h.stop()
    }

    @Test
    fun applyWithSelectedAccountMissingFromListIsNotConnected() = runTest {
        val h = harness()
        h.repo.apply(payload(selected = "gone"))
        assertEquals(WatchSessionState.NotConnected, h.repo.state.value)
        assertNull(h.active.bearerToken())

        h.repo.apply(payload(selected = null))
        assertEquals(WatchSessionState.NotConnected, h.repo.state.value)
        assertNull(h.active.bearerToken())
        h.stop()
    }

    @Test
    fun applyOverwritesPreviousSelection() = runTest {
        val h = harness()
        h.repo.apply(payload(token = "old", selected = "a1"))
        h.repo.apply(payload(token = "new", selected = "a2"))

        assertEquals(WatchSessionState.Connected("a2", isDemo = false), h.repo.state.value)
        assertEquals(Credential.ApiToken("new"), h.vault.read())
        assertEquals("new", h.active.bearerToken())
        assertEquals("a2", h.prefs.selectedAccountId())
        h.stop()
    }

    @Test
    fun clearWipesVaultAndGoesNotConnected() = runTest {
        val h = harness()
        h.repo.apply(payload())
        h.repo.clear()

        assertEquals(WatchSessionState.NotConnected, h.repo.state.value)
        assertNull(h.vault.read())
        assertNull(h.active.bearerToken())
        assertNull(h.prefs.selectedAccountId())
        assertNull(h.prefs.lastCheckInMillis())
        h.stop()
    }

    @Test
    fun staleAfter24hWithoutCheckIn() = runTest {
        val h = harness()
        h.repo.apply(payload())
        h.now = START + StalenessPolicy.MAX_OFFLINE_MILLIS + 1
        h.repo.refreshStaleness()

        assertEquals(WatchSessionState.Stale, h.repo.state.value)
        assertNull(h.active.bearerToken())
        h.stop()
    }

    @Test
    fun connectedAtExactly24h() = runTest {
        val h = harness()
        h.repo.apply(payload())
        h.now = START + StalenessPolicy.MAX_OFFLINE_MILLIS
        h.repo.refreshStaleness()

        assertEquals(WatchSessionState.Connected("a1", isDemo = false), h.repo.state.value)
        assertEquals("tok", h.active.bearerToken())
        h.stop()
    }

    @Test
    fun applyRefreshesCheckIn() = runTest {
        val h = harness()
        h.repo.apply(payload())
        h.now = START + StalenessPolicy.MAX_OFFLINE_MILLIS + 10
        h.repo.refreshStaleness()
        assertEquals(WatchSessionState.Stale, h.repo.state.value)

        h.repo.apply(payload())
        assertEquals(WatchSessionState.Connected("a1", isDemo = false), h.repo.state.value)
        assertEquals("tok", h.active.bearerToken())
        // The watch's own clock is stored, not the phone's sentAtMillis (5).
        assertEquals(h.now, h.prefs.lastCheckInMillis())
        h.stop()
    }

    @Test
    fun unauthorizedWipesVault() = runTest {
        val h = harness()
        h.repo.apply(payload())
        h.repo.onUnauthorized(h.repo.issuedCredential()!!.generation)

        assertEquals(WatchSessionState.NotConnected, h.repo.state.value)
        assertNull(h.vault.read())
        assertNull(h.active.bearerToken())
        h.stop()
    }

    @Test
    fun unauthorizedForAReplacedTokenKeepsTheNewSession() = runTest {
        val h = harness()
        h.repo.apply(payload(token = "old"))
        val oldGeneration = h.repo.issuedCredential()!!.generation
        h.repo.apply(payload(token = "new", selected = "a2"))

        h.repo.onUnauthorized(oldGeneration)

        assertEquals(WatchSessionState.Connected("a2", isDemo = false), h.repo.state.value)
        assertEquals(Credential.ApiToken("new"), h.vault.read())
        assertEquals("new", h.active.bearerToken())
        h.stop()
    }

    @Test
    fun reapplyingTheSameTokenKeepsItsGenerationSoAGenuine401StillWipes() = runTest {
        val h = harness()
        h.repo.apply(payload(token = "tok"))
        val sent = h.repo.issuedCredential()!!.generation
        h.now += 1_000L
        h.repo.apply(payload(token = "tok"))

        assertEquals(sent, h.repo.issuedCredential()!!.generation)
        h.repo.onUnauthorized(sent)
        assertEquals(WatchSessionState.NotConnected, h.repo.state.value)
        assertNull(h.vault.read())
        h.stop()
    }

    @Test
    fun aDisconnectedSessionIssuesNoCredential() = runTest {
        val h = harness()
        h.repo.apply(payload())
        h.repo.clear()

        assertNull(h.repo.issuedCredential())
        h.stop()
    }

    @Test
    fun restoreWithEmptyVaultIsNotConnected() = runTest {
        val h = harness()
        assertEquals(WatchSessionState.Restoring, h.repo.state.value)
        h.repo.restore()

        assertEquals(WatchSessionState.NotConnected, h.repo.state.value)
        h.stop()
    }

    @Test
    fun restoreWithFreshCheckInReconnects() = runTest {
        val h = harness()
        h.repo.apply(payload(selected = "a2"))
        val active = ActiveCredential()
        val second = WatchSessionRepository(h.vault, active, h.demoMode, h.prefs) { h.now }
        second.restore()

        assertEquals(WatchSessionState.Connected("a2", isDemo = false), second.state.value)
        assertEquals("tok", active.bearerToken())
        h.stop()
    }

    @Test
    fun restoreWithStaleCheckInIsStaleAndStopsUsingToken() = runTest {
        val h = harness()
        h.repo.apply(payload())
        h.now = START + StalenessPolicy.MAX_OFFLINE_MILLIS + 1
        val active = ActiveCredential()
        val second = WatchSessionRepository(h.vault, active, h.demoMode, h.prefs) { h.now }
        second.restore()

        assertEquals(WatchSessionState.Stale, second.state.value)
        assertNull(active.bearerToken())
        h.stop()
    }

    @Test
    fun invalidPayloadLeavesStateUnchanged() = runTest {
        val h = harness()
        h.repo.apply(payload())
        val before = h.repo.state.value

        val tooNew = HandoffCodec.encode(payload().copy(version = HandoffPayload.CURRENT_VERSION + 1, token = "evil"))
        assertFalse(h.repo.applyEncoded(tooNew))
        assertFalse(h.repo.applyEncoded("not json".encodeToByteArray()))

        assertEquals(before, h.repo.state.value)
        assertEquals(Credential.ApiToken("tok"), h.vault.read())
        assertEquals("tok", h.active.bearerToken())
        h.stop()
    }

    @Test
    fun validEncodedPayloadIsApplied() = runTest {
        val h = harness()
        assertTrue(h.repo.applyEncoded(HandoffCodec.encode(payload(selected = "a2"))))
        assertEquals(WatchSessionState.Connected("a2", isDemo = false), h.repo.state.value)
        h.stop()
    }

    private companion object {
        const val START = 1_000_000L
    }
}
