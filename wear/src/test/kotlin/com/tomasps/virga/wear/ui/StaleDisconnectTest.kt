package com.tomasps.virga.wear.ui

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.tomasps.virga.core.auth.Credential
import com.tomasps.virga.core.auth.session.ActiveCredential
import com.tomasps.virga.core.auth.vault.CredentialVault
import com.tomasps.virga.core.auth.vault.SealedBytes
import com.tomasps.virga.core.auth.vault.SecretCipher
import com.tomasps.virga.core.common.DispatcherProvider
import com.tomasps.virga.core.network.demo.DemoMode
import com.tomasps.virga.core.wearsync.HandoffAccount
import com.tomasps.virga.core.wearsync.HandoffPayload
import com.tomasps.virga.core.wearsync.StalenessPolicy
import com.tomasps.virga.wear.session.WatchDisconnect
import com.tomasps.virga.wear.session.WatchSessionPrefs
import com.tomasps.virga.wear.session.WatchSessionRepository
import com.tomasps.virga.wear.session.WatchSessionState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The stale screen's Disconnect over a real session: only a confirmed Disconnect clears the vault and the prefs. */
@OptIn(ExperimentalCoroutinesApi::class)
class StaleDisconnectTest {

    @get:Rule val main = MainDispatcherRule()

    @get:Rule val folder = TemporaryFolder()

    private class PlainCipher : SecretCipher {
        override fun encrypt(plaintext: ByteArray) = SealedBytes(ByteArray(1), plaintext)
        override fun decrypt(sealed: SealedBytes): ByteArray = sealed.ciphertext
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

        suspend fun goStale() {
            repo.apply(
                HandoffPayload(HandoffPayload.CURRENT_VERSION, "tok", false, listOf(HandoffAccount("a1", "One")), "a1", 5L),
            )
            now = START + StalenessPolicy.MAX_OFFLINE_MILLIS + 1
            repo.refreshStaleness()
        }

        fun viewModel() = main.keep(BuildListViewModel::class.java) {
            BuildListViewModel(repo.state, FakeWearBuilds(), WatchDisconnect { repo.clear() })
        }

        fun stop() = scope.coroutineContext[Job]!!.cancel()
    }

    @Test
    fun confirmedDisconnectClearsVaultAndPrefsAndShowsNotConnected() = main.runVmTest {
        val h = Harness(this)
        h.goStale()
        val vm = h.viewModel()
        advanceUntilIdle()
        assertEquals(ListState.Stale, vm.state.value)

        vm.askDisconnect()
        assertEquals(ListState.ConfirmDisconnect, vm.state.value)
        vm.confirmDisconnect()
        advanceUntilIdle()

        assertEquals(WatchSessionState.NotConnected, h.repo.state.value)
        assertEquals(ListState.NotConnected, vm.state.value)
        assertNull(h.vault.read())
        assertNull(h.prefs.lastCheckInMillis())
        assertTrue(h.prefs.accountIds().isEmpty())
        h.stop()
    }

    @Test
    fun disconnectWithoutConfirmClearsNothing() = main.runVmTest {
        val h = Harness(this)
        h.goStale()
        val vm = h.viewModel()
        advanceUntilIdle()

        vm.askDisconnect()
        vm.dismissDisconnect()
        advanceUntilIdle()

        assertEquals(ListState.Stale, vm.state.value)
        assertEquals(WatchSessionState.Stale, h.repo.state.value)
        assertEquals(Credential.ApiToken("tok"), h.vault.read())
        h.stop()
    }

    @Test
    fun confirmWithoutAskingClearsNothing() = main.runVmTest {
        val h = Harness(this)
        h.goStale()
        val vm = h.viewModel()
        advanceUntilIdle()

        vm.confirmDisconnect()
        advanceUntilIdle()

        assertEquals(WatchSessionState.Stale, h.repo.state.value)
        assertEquals(Credential.ApiToken("tok"), h.vault.read())
        h.stop()
    }

    private companion object {
        const val START = 1_000_000L
    }
}
