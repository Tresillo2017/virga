package com.tomasps.virga.wearsync

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.tomasps.virga.core.auth.Credential
import com.tomasps.virga.core.auth.session.CloudflareAccount
import com.tomasps.virga.core.auth.session.SessionRepository
import com.tomasps.virga.core.auth.session.SessionState
import com.tomasps.virga.core.auth.vault.CredentialVault
import com.tomasps.virga.core.auth.vault.SealedBytes
import com.tomasps.virga.core.auth.vault.SecretCipher
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.wearsync.HandoffAccount
import com.tomasps.virga.core.wearsync.HandoffPayload
import com.tomasps.virga.core.wearsync.HandoffTransport
import com.tomasps.virga.core.common.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class HandoffPublisherTest {

    @get:Rule
    val folder = TemporaryFolder()

    private class FakeTransport : HandoffTransport {
        val published = mutableListOf<HandoffPayload>()
        var clears = 0
        var failClear = false
        var failPublish = false
        override suspend fun publish(payload: HandoffPayload) {
            if (failPublish) error("data layer down")
            published += payload
        }
        override suspend fun clear() {
            if (failClear) error("data layer down")
            clears++
        }
    }

    private class FakePendingClear : PendingClearStore {
        var flag = false
        var everPublished = false
        override suspend fun isSet() = flag
        override suspend fun set(value: Boolean) { flag = value }
        override suspend fun hasEverPublished() = everPublished
        override suspend fun markPublished() { everPublished = true }
    }

    private class FakeSession(initial: SessionState) : SessionRepository {
        override val state = MutableStateFlow(initial)
        override suspend fun restore() = Unit
        override suspend fun signInWithToken(token: String): ApiResult<Unit> = error("unused")
        override suspend fun enterDemo(): ApiResult<Unit> = error("unused")
        override suspend fun selectAccount(accountId: String) = Unit
        override suspend fun signOut() = Unit
    }

    /** Identity "encryption": the vault round-trips without Keystore. */
    private class PlainCipher(private val failDecrypt: Boolean = false) : SecretCipher {
        override fun encrypt(plaintext: ByteArray) = SealedBytes(ByteArray(1), plaintext)
        override fun decrypt(sealed: SealedBytes): ByteArray {
            if (failDecrypt) error("lost key")
            return sealed.ciphertext
        }
    }

    private fun TestScope2.vault(cipher: SecretCipher = PlainCipher()) = CredentialVault(
        PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "v.preferences_pb") },
        cipher,
        // Keeps vault reads on the test dispatcher so a publish has finished by the time the test asserts.
        object : DispatcherProvider {
            override val io: CoroutineDispatcher = dispatcher
            override val default: CoroutineDispatcher = dispatcher
        },
    )

    private class TestScope2(val scope: CoroutineScope, val dispatcher: CoroutineDispatcher)

    private val accounts = listOf(CloudflareAccount("a1", "One"), CloudflareAccount("a2", "Two"))
    private fun signedIn(selected: String? = "a1", demo: Boolean = false) =
        SessionState.SignedIn(accounts, selected, demo)

    private fun TestScope2.publisher(
        session: SessionRepository,
        vault: CredentialVault,
        transport: HandoffTransport,
        pendingClear: PendingClearStore = FakePendingClear(),
        scheduled: () -> Unit = {},
        isUnavailable: (Exception) -> Boolean = { false },
        now: () -> Long = { 1_000L },
    ) = HandoffPublisher(session, vault, transport, now, pendingClear, scheduled, {}, isUnavailable)

    private fun kotlinx.coroutines.test.TestScope.harness() =
        UnconfinedTestDispatcher(testScheduler).let { TestScope2(CoroutineScope(Job() + it), it) }

    @Test
    fun signedInPublishesTokenAccountsAndSelection() = runTest {
        val h = harness()
        val vault = h.vault().also { it.write(Credential.ApiToken("tok")) }
        val transport = FakeTransport()
        h.publisher(FakeSession(signedIn("a2")), vault, transport).start(h.scope)

        val p = transport.published.single()
        assertEquals("tok", p.token)
        assertEquals(false, p.isDemo)
        assertEquals(listOf(HandoffAccount("a1", "One"), HandoffAccount("a2", "Two")), p.accounts)
        assertEquals("a2", p.selectedAccountId)
        assertEquals(HandoffPayload.CURRENT_VERSION, p.version)
        assertEquals(1_000L, p.sentAtMillis)
        h.scope.coroutineContext[Job]!!.cancel()
    }

    @Test
    fun demoPublishesNoTokenAndDemoFlag() = runTest {
        val h = harness()
        val vault = h.vault().also { it.write(Credential.Demo) }
        val transport = FakeTransport()
        h.publisher(FakeSession(signedIn(demo = true)), vault, transport).start(h.scope)

        val p = transport.published.single()
        assertNull(p.token)
        assertTrue(p.isDemo)
        h.scope.coroutineContext[Job]!!.cancel()
    }

    @Test
    fun signOutClearsTransport() = runTest {
        val h = harness()
        val vault = h.vault().also { it.write(Credential.ApiToken("tok")) }
        val transport = FakeTransport()
        val session = FakeSession(signedIn())
        h.publisher(session, vault, transport).start(h.scope)

        session.state.value = SessionState.SignedOut

        assertEquals(1, transport.clears)
        h.scope.coroutineContext[Job]!!.cancel()
    }

    @Test
    fun restoringPublishesNothing() = runTest {
        val h = harness()
        val vault = h.vault().also { it.write(Credential.ApiToken("tok")) }
        val transport = FakeTransport()
        h.publisher(FakeSession(SessionState.Restoring), vault, transport).start(h.scope)

        assertTrue(transport.published.isEmpty())
        assertEquals(0, transport.clears)
        h.scope.coroutineContext[Job]!!.cancel()
    }

    @Test
    fun accountSwitchRepublishesWithNewSelection() = runTest {
        val h = harness()
        val vault = h.vault().also { it.write(Credential.ApiToken("tok")) }
        val transport = FakeTransport()
        val session = FakeSession(signedIn("a1"))
        h.publisher(session, vault, transport).start(h.scope)

        session.state.value = signedIn("a2")

        assertEquals(listOf("a1", "a2"), transport.published.map { it.selectedAccountId })
        h.scope.coroutineContext[Job]!!.cancel()
    }

    @Test
    fun heartbeatRepublishesWithFreshTimestamp() = runTest {
        val h = harness()
        val vault = h.vault().also { it.write(Credential.ApiToken("tok")) }
        val transport = FakeTransport()
        var time = 1_000L
        val publisher = h.publisher(FakeSession(signedIn()), vault, transport) { time }
        publisher.start(h.scope)

        time = 9_000L
        publisher.heartbeat()

        assertEquals(listOf(1_000L, 9_000L), transport.published.map { it.sentAtMillis })
        h.scope.coroutineContext[Job]!!.cancel()
    }

    @Test
    fun unreadableVaultPublishesNothing() = runTest {
        val h = harness()
        val vault = h.vault(PlainCipher(failDecrypt = true)).also { it.write(Credential.ApiToken("tok")) }
        val transport = FakeTransport()
        h.publisher(FakeSession(signedIn()), vault, transport).start(h.scope)

        assertTrue(transport.published.isEmpty())
        h.scope.coroutineContext[Job]!!.cancel()
    }

    @Test
    fun heartbeatWaitsForRestoringToSettleThenPublishesFreshTimestamp() = runTest {
        val h = harness()
        val vault = h.vault().also { it.write(Credential.ApiToken("tok")) }
        val transport = FakeTransport()
        val session = FakeSession(SessionState.Restoring)
        var time = 1_000L
        val publisher = h.publisher(session, vault, transport) { time }

        val result = async { publisher.heartbeat() }
        runCurrent()
        assertTrue(transport.published.isEmpty())
        time = 9_000L
        session.state.value = signedIn()
        advanceUntilIdle()

        assertTrue(result.await())
        assertEquals(listOf(9_000L), transport.published.map { it.sentAtMillis })
        h.scope.coroutineContext[Job]!!.cancel()
    }

    @Test
    fun heartbeatReportsNotDoneWhenSessionNeverSettles() = runTest {
        val h = harness()
        val vault = h.vault().also { it.write(Credential.ApiToken("tok")) }
        val transport = FakeTransport()
        val publisher = h.publisher(FakeSession(SessionState.Restoring), vault, transport)

        val result = async { publisher.heartbeat() }
        advanceUntilIdle()

        assertEquals(false, result.await())
        assertTrue(transport.published.isEmpty())
        h.scope.coroutineContext[Job]!!.cancel()
    }

    @Test
    fun demoFlaggedStateNeverPublishesAToken() = runTest {
        val h = harness()
        val vault = h.vault().also { it.write(Credential.ApiToken("tok")) }
        val transport = FakeTransport()
        h.publisher(FakeSession(signedIn(demo = true)), vault, transport).start(h.scope)

        val p = transport.published.single()
        assertNull(p.token)
        assertTrue(p.isDemo)
        h.scope.coroutineContext[Job]!!.cancel()
    }

    @Test
    fun failedClearKeepsPendingFlagUntilRetrySucceeds() = runTest {
        val h = harness()
        val vault = h.vault()
        val transport = FakeTransport().apply { failClear = true }
        val pending = FakePendingClear().apply { everPublished = true }
        val session = FakeSession(SessionState.SignedOut)
        val publisher = h.publisher(session, vault, transport, pendingClear = pending)
        publisher.start(h.scope)

        assertTrue(pending.flag)
        assertEquals(false, publisher.retryPendingClear(attempt = 0))
        assertTrue(pending.flag)

        transport.failClear = false
        assertTrue(publisher.retryPendingClear(attempt = 1))
        assertEquals(1, transport.clears)
        assertEquals(false, pending.flag)
        h.scope.coroutineContext[Job]!!.cancel()
    }

    @Test
    fun signingBackInDropsAPendingClearSoItCannotWipeTheNewCredential() = runTest {
        val h = harness()
        val vault = h.vault().also { it.write(Credential.ApiToken("tok")) }
        val transport = FakeTransport().apply { failClear = true }
        val pending = FakePendingClear().apply { everPublished = true }
        val session = FakeSession(SessionState.SignedOut)
        val publisher = h.publisher(session, vault, transport, pendingClear = pending)
        publisher.start(h.scope)
        assertTrue(pending.flag)

        session.state.value = signedIn()
        transport.failClear = false

        assertEquals(false, pending.flag)
        assertTrue(publisher.retryPendingClear(attempt = 0))
        assertEquals(0, transport.clears)
        h.scope.coroutineContext[Job]!!.cancel()
    }

    @Test
    fun neverPublishedUserNeverSetsFlagOrSchedulesRetry() = runTest {
        val h = harness()
        val transport = FakeTransport().apply { failClear = true }
        val pending = FakePendingClear()
        var scheduled = 0
        h.publisher(FakeSession(SessionState.SignedOut), h.vault(), transport, pending, { scheduled++ }).start(h.scope)

        assertEquals(false, pending.flag)
        assertEquals(0, scheduled)
        assertEquals(0, transport.clears)
        h.scope.coroutineContext[Job]!!.cancel()
    }

    @Test
    fun permanentUnavailabilityClearsFlagAndStopsRetrying() = runTest {
        val h = harness()
        val transport = FakeTransport().apply { failClear = true }
        val pending = FakePendingClear().apply { everPublished = true }
        var scheduled = 0
        val publisher = h.publisher(
            FakeSession(SessionState.SignedOut), h.vault(), transport, pending, { scheduled++ }, { true },
        )
        publisher.start(h.scope)

        assertEquals(false, pending.flag)
        assertEquals(0, scheduled)
        assertTrue(publisher.retryPendingClear(attempt = 0))
        h.scope.coroutineContext[Job]!!.cancel()
    }

    @Test
    fun clearRetryStopsAfterMaxAttemptsAndKeepsFlagForNextColdStart() = runTest {
        val h = harness()
        val transport = FakeTransport().apply { failClear = true }
        val pending = FakePendingClear().apply { everPublished = true; flag = true }
        val publisher = h.publisher(FakeSession(SessionState.SignedOut), h.vault(), transport, pending)

        assertEquals(false, publisher.retryPendingClear(attempt = 0))
        assertTrue(publisher.retryPendingClear(attempt = MAX_CLEAR_ATTEMPTS - 1))
        assertTrue(pending.flag)
        h.scope.coroutineContext[Job]!!.cancel()
    }

    @Test
    fun heartbeatSucceedsEvenWhenThePublishFails() = runTest {
        val h = harness()
        val vault = h.vault().also { it.write(Credential.ApiToken("tok")) }
        val transport = FakeTransport().apply { failPublish = true }
        val publisher = h.publisher(FakeSession(signedIn()), vault, transport)

        assertTrue(publisher.heartbeat())
        h.scope.coroutineContext[Job]!!.cancel()
    }
}
