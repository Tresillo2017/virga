package com.tomasps.virga.core.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.tomasps.virga.core.auth.session.ActiveCredential
import com.tomasps.virga.core.auth.session.SessionPreferences
import com.tomasps.virga.core.auth.vault.AesGcmCipher
import com.tomasps.virga.core.auth.vault.CredentialVault
import com.tomasps.virga.core.network.CloudflareApi
import com.tomasps.virga.core.network.RateLimiter
import com.tomasps.virga.core.network.demo.DemoMode
import com.tomasps.virga.core.network.demo.DemoRouteRegistry
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.rules.TemporaryFolder
import java.io.File
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** Everything a session test needs, wired like production but backed by a temp file and MockEngine. */
class SessionHarness(
    val folder: TemporaryFolder,
    val live: (HttpRequestData) -> Unit = {},
) {
    val demoMode = DemoMode()
    val demoRoutes = DemoRouteRegistry()
    val active = ActiveCredential()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val store: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) {
        File(folder.root, "session.preferences_pb")
    }
    val vault = CredentialVault(store, AesGcmCipher { testKey })
    val prefs = SessionPreferences(store)

    /** Live engine: the handler sees the Authorization header, which the real client must send. */
    fun api(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): CloudflareApi =
        CloudflareApi.create(
            credentials = active,
            demoMode = demoMode,
            demoRoutes = demoRoutes,
            rateLimiter = RateLimiter(),
            liveEngine = MockEngine { request -> live(request); handler(request) },
        )
}

private val testKey: SecretKey by lazy { KeyGenerator.getInstance("AES").apply { init(256) }.generateKey() }

/** JSON body in the Cloudflare envelope with one success result. */
fun envelope(result: String, resultInfo: String? = null): String {
    val info = resultInfo?.let { ",\"result_info\":$it" }.orEmpty()
    return "{\"success\":true,\"errors\":[],\"messages\":[],\"result\":$result$info}"
}

fun MockRequestHandleScope.jsonReply(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
    respond(content = body, status = status, headers = headersOf(HttpHeaders.ContentType, "application/json"))

fun authorizationOf(request: HttpRequestData): String? = request.headers[HttpHeaders.Authorization]
