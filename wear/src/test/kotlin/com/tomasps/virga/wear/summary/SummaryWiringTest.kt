package com.tomasps.virga.wear.summary

import com.tomasps.virga.core.auth.vault.SealedBytes
import com.tomasps.virga.core.auth.vault.SecretCipher
import com.tomasps.virga.core.network.CloudflareApi
import com.tomasps.virga.core.network.CredentialProvider
import com.tomasps.virga.core.network.demo.DemoMode
import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.demo.DemoRouteRegistry
import com.tomasps.virga.core.auth.session.ActiveCredential
import com.tomasps.virga.core.wearsync.HandoffAccount
import com.tomasps.virga.core.wearsync.HandoffPayload
import com.tomasps.virga.wear.WATCH_SESSION_STATE
import com.tomasps.virga.wear.session.WatchSessionRepository
import com.tomasps.virga.wear.session.WatchSessionState
import com.tomasps.virga.wear.wearKoinModules
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.Koin
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * The production [SummarySource] resolved from the real graph, with only the engine (always a 401) and the keystore
 * cipher replaced. Proves the wiring passes the real session wipe and token check, so a default cannot slip in.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class SummaryWiringTest {

    private val now = Instant.parse("2026-11-14T12:00:00Z")

    private val testOverrides = module {
        single<SecretCipher> {
            object : SecretCipher {
                override fun encrypt(plaintext: ByteArray) = SealedBytes(ByteArray(12), plaintext)
                override fun decrypt(sealed: SealedBytes) = sealed.ciphertext
            }
        }
        single<CloudflareApi> {
            val registry = DemoRouteRegistry().apply {
                graphQl(SUMMARY_OPERATION) { DemoReply.failure(HttpStatusCode.Unauthorized, 9109, "rejected") }
            }
            CloudflareApi.create(
                credentials = get<CredentialProvider>(),
                demoMode = get<DemoMode>(),
                demoRoutes = registry,
                liveEngine = registry.toMockEngine(Dispatchers.Unconfined),
            )
        }
    }

    private fun withGraph(block: suspend (Koin) -> Unit) = runTest {
        val koin = koinApplication {
            androidContext(RuntimeEnvironment.getApplication())
            modules(wearKoinModules + testOverrides)
        }.koin
        try {
            block(koin)
        } finally {
            koin.close()
        }
    }

    private val payload = HandoffPayload(
        version = HandoffPayload.CURRENT_VERSION,
        token = "tok",
        isDemo = false,
        accounts = listOf(HandoffAccount("acc-1", "One")),
        selectedAccountId = "acc-1",
        sentAtMillis = 0L,
    )

    @Test
    fun a401WithATokenPresentWipesTheSessionThroughTheProductionWiring() = withGraph { koin ->
        koin.get<WatchSessionRepository>().apply(payload)
        val state = koin.get<StateFlow<WatchSessionState>>(WATCH_SESSION_STATE)
        assertEquals(WatchSessionState.Connected("acc-1", false), state.value)

        val result = koin.get<SummarySource>().last24h("acc-1", now)

        assertTrue(result is SummaryResult.Failed)
        assertEquals(WatchSessionState.NotConnected, state.value)
    }

    @Test
    fun a401WithNoTokenDoesNotWipeTheSession() = withGraph { koin ->
        val state = koin.get<StateFlow<WatchSessionState>>(WATCH_SESSION_STATE)
        assertEquals(WatchSessionState.Restoring, state.value)
        assertEquals(null, koin.get<ActiveCredential>().bearerToken())

        val result = koin.get<SummarySource>().last24h("acc-1", now)

        assertTrue(result is SummaryResult.Failed)
        assertEquals("no token means nothing was rejected", WatchSessionState.Restoring, state.value)
    }
}
