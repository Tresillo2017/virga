package com.tomasps.virga.wear.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.tomasps.virga.core.auth.Credential
import com.tomasps.virga.core.auth.vault.CredentialVault
import com.tomasps.virga.core.auth.vault.SealedBytes
import com.tomasps.virga.core.auth.vault.SecretCipher
import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.CloudflareApi
import com.tomasps.virga.core.network.CredentialProvider
import com.tomasps.virga.core.network.demo.DemoMode
import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.demo.DemoRouteRegistry
import com.tomasps.virga.core.wearsync.HandoffAccount
import com.tomasps.virga.core.wearsync.HandoffPayload
import com.tomasps.virga.feature.builds.watch.BuildFetcher
import com.tomasps.virga.wear.WATCH_SESSION_STATE
import com.tomasps.virga.wear.builds.WearBuilds
import com.tomasps.virga.wear.summary.SUMMARY_OPERATION
import com.tomasps.virga.wear.summary.SummaryResult
import com.tomasps.virga.wear.summary.SummarySource
import com.tomasps.virga.wear.wearKoinModules
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.Koin
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant

private const val TIMEOUT_MILLIS = 10_000L
private const val SCRIPTS = "/accounts/{accountId}/workers/scripts"
private const val BUILDS = "/accounts/{accountId}/builds/workers/{workerTag}/builds"
private const val BUILD = "/accounts/{accountId}/builds/builds/{buildUuid}"
private const val BUILD_JSON =
    """{"build_uuid":"b-1","status":"running","created_on":"2026-11-14T10:00:00Z"}"""

/**
 * The production graph (session, guarded credential provider, sources and fetcher) with only the storage, the cipher
 * and the engine replaced. A handoff [WatchSessionRepository.apply] that is slow on its vault write overlaps normal
 * traffic, which happens on every phone cold start and heartbeat; none of it may wipe the session the apply installs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class HandoffOverlapTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val storeScope = CoroutineScope(Job() + Dispatchers.IO)

    /** Parks writes while armed, so an apply() holds the session lock mid-way, like a slow disk. */
    private class GatedStore(private val delegate: DataStore<Preferences>) : DataStore<Preferences> {
        @Volatile var gate: CompletableDeferred<Unit>? = null
        val entered = CompletableDeferred<Unit>()
        override val data: Flow<Preferences> = delegate.data
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            gate?.let { entered.complete(Unit); it.await() }
            return delegate.updateData(transform)
        }
    }

    /** Reports every token read, so the test knows the requests are in flight before it releases the gate. */
    private class SignallingProvider(private val delegate: CredentialProvider) : CredentialProvider {
        val reads = Channel<Unit>(Channel.UNLIMITED)
        override suspend fun bearerToken(): String? {
            reads.trySend(Unit)
            return delegate.bearerToken()
        }
    }

    private val vaultStore by lazy {
        GatedStore(PreferenceDataStoreFactory.create(scope = storeScope) { File(folder.root, "vault.preferences_pb") })
    }
    private lateinit var provider: SignallingProvider

    /** Each test sets the build-record reply; the default answers 200. */
    @Volatile private var buildReply: suspend () -> DemoReply = { DemoReply.ok(Json.parseToJsonElement(BUILD_JSON)) }

    private fun routes() = DemoRouteRegistry().apply {
        get(SCRIPTS) { DemoReply.ok(Json.parseToJsonElement("""[{"id":"api","tag":"tag-api"}]""")) }
        get(BUILDS) { DemoReply.ok(Json.parseToJsonElement("[$BUILD_JSON]")) }
        get(BUILD) { buildReply() }
        graphQl(SUMMARY_OPERATION) { DemoReply.graphQl(null) }
    }

    private val overrides = module {
        single<SecretCipher> {
            object : SecretCipher {
                override fun encrypt(plaintext: ByteArray) = SealedBytes(ByteArray(12), plaintext)
                override fun decrypt(sealed: SealedBytes) = sealed.ciphertext
            }
        }
        single { CredentialVault(vaultStore, get(), get()) }
        single<CloudflareApi> {
            provider = SignallingProvider(get<CredentialProvider>())
            val registry = routes()
            CloudflareApi.create(
                credentials = provider,
                demoMode = get<DemoMode>(),
                demoRoutes = registry,
                liveEngine = registry.toMockEngine(Dispatchers.Unconfined),
            )
        }
    }

    private lateinit var koin: Koin

    private fun withGraph(block: suspend CoroutineScope.() -> Unit) = runBlocking {
        koin = koinApplication {
            androidContext(RuntimeEnvironment.getApplication())
            modules(wearKoinModules + overrides)
        }.koin
        try {
            koin.get<CloudflareApi>()
            withTimeout(TIMEOUT_MILLIS) { block() }
        } finally {
            koin.close()
        }
    }

    @After
    fun tearDown() = storeScope.cancel()

    private fun payload(token: String, account: String) = HandoffPayload(
        version = HandoffPayload.CURRENT_VERSION,
        token = token,
        isDemo = false,
        accounts = listOf(HandoffAccount(account, account)),
        selectedAccountId = account,
        sentAtMillis = 0L,
    )

    private val session get() = koin.get<WatchSessionRepository>()
    private val state get() = koin.get<StateFlow<WatchSessionState>>(WATCH_SESSION_STATE)

    private suspend fun awaitReads(count: Int) = repeat(count) { provider.reads.receive() }

    private suspend fun drainReads() {
        while (provider.reads.tryReceive().isSuccess) Unit
    }

    private suspend fun assertNewSessionKept() {
        assertEquals(WatchSessionState.Connected("acc-new", isDemo = false), state.value)
        assertEquals(Credential.ApiToken("new-token"), koin.get<CredentialVault>().read())
        assertNotNull(koin.get<WatchSessionPrefs>().lastCheckInMillis())
    }

    @Test
    fun requestsDuringASlowApplyNeverWipeTheNewSession() = withGraph {
        session.apply(payload("old-token", "acc-old"))
        drainReads()
        val gate = CompletableDeferred<Unit>()
        vaultStore.gate = gate
        val applying = async(Dispatchers.Default) { session.apply(payload("new-token", "acc-new")) }
        vaultStore.entered.await()

        val builds = koin.get<WearBuilds>()
        val requests = listOf(
            async(Dispatchers.Default) { builds.recent("acc-old") },
            async(Dispatchers.Default) { builds.latest("acc-old") },
            async(Dispatchers.Default) { koin.get<SummarySource>().last24h("acc-old", Instant.now()) },
            async(Dispatchers.Default) { koin.get<BuildFetcher>().fetch("acc-old", "api", "b-1") },
        )
        awaitReads(requests.size)
        vaultStore.gate = null
        gate.complete(Unit)
        applying.await()
        val results = requests.awaitAll()

        assertNewSessionKept()
        results.forEach { result ->
            val error = (result as? ApiResult.Failure)?.error ?: (result as? SummaryResult.Failed)?.error
            assertFalse("no request may fail as unauthorized: $result", error is ApiError.Unauthorized)
        }
    }

    @Test
    fun aLate401ForTheOldTokenAfterAnAccountSwitchKeepsTheNewToken() = withGraph {
        session.apply(payload("old-token", "acc-old"))
        drainReads()
        val reply = CompletableDeferred<Unit>()
        buildReply = {
            reply.await()
            DemoReply.failure(HttpStatusCode.Unauthorized, 10000, "Authentication error")
        }
        val polling = async(Dispatchers.Default) { koin.get<BuildFetcher>().fetch("acc-old", "api", "b-1") }
        awaitReads(1)

        session.apply(payload("new-token", "acc-new"))
        reply.complete(Unit)
        val result = polling.await()

        assertTrue(result is ApiResult.Failure && result.error is ApiError.Unauthorized)
        assertNewSessionKept()
    }

    @Test
    fun aGenuine401ForTheCurrentTokenStillWipes() = withGraph {
        session.apply(payload("new-token", "acc-new"))
        buildReply = { DemoReply.failure(HttpStatusCode.Unauthorized, 10000, "Authentication error") }

        koin.get<BuildFetcher>().fetch("acc-new", "api", "b-1")

        assertEquals(WatchSessionState.NotConnected, state.value)
        assertEquals(null, koin.get<CredentialVault>().read())
    }
}
