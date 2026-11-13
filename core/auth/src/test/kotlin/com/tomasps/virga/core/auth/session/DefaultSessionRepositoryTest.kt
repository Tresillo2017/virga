package com.tomasps.virga.core.auth.session

import com.tomasps.virga.core.auth.Credential
import com.tomasps.virga.core.auth.SessionHarness
import com.tomasps.virga.core.auth.authorizationOf
import com.tomasps.virga.core.auth.envelope
import com.tomasps.virga.core.auth.jsonReply
import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.CloudflareEnvelopes
import com.tomasps.virga.core.network.demo.DemoReply
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val GOOD_TOKEN = "cf-good-token"
private const val ACCOUNT_A = "acc-a"
private const val ACCOUNT_B = "acc-b"
private const val FULL_PAGE = 50

class DefaultSessionRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `sign in verifies the token, loads accounts, saves the token and selects the first account`() = runBlocking {
        // Arrange
        val seenAuth = mutableListOf<String?>()
        val harness = SessionHarness(folder, live = { seenAuth += authorizationOf(it) })
        val api = harness.api { request ->
            when (request.url.encodedPath) {
                "/client/v4/user/tokens/verify" -> jsonReply(envelope("""{"id":"t1","status":"active"}"""))
                "/client/v4/accounts" -> jsonReply(envelope(accountsJson(ACCOUNT_A to "Alpha")))
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val repository = DefaultSessionRepository(api, harness.active, harness.demoMode, harness.vault, harness.prefs)

        // Act
        val result = repository.signInWithToken(GOOD_TOKEN)

        // Assert
        assertTrue(result is ApiResult.Success)
        val signedIn = repository.state.value as SessionState.SignedIn
        assertEquals(listOf(ACCOUNT_A), signedIn.accounts.map { it.id })
        assertEquals(ACCOUNT_A, signedIn.selectedAccountId)
        assertFalse(signedIn.isDemo)
        assertEquals(Credential.ApiToken(GOOD_TOKEN), harness.vault.read())
        assertEquals(listOf("Bearer $GOOD_TOKEN", "Bearer $GOOD_TOKEN"), seenAuth)
    }

    @Test
    fun `an inactive token is rejected, nothing is saved and the old token is restored`() = runBlocking {
        // Arrange
        val harness = SessionHarness(folder)
        val api = harness.api { jsonReply(envelope("""{"id":"t1","status":"disabled"}""")) }
        val repository = DefaultSessionRepository(api, harness.active, harness.demoMode, harness.vault, harness.prefs)

        // Act
        val result = repository.signInWithToken("cf-disabled")

        // Assert
        assertEquals(ApiError.Unauthorized(), (result as ApiResult.Failure).error)
        assertNull(harness.vault.read())
        assertNull(harness.active.bearerToken())
        assertEquals(SessionState.Restoring, repository.state.value)
    }

    @Test
    fun `a 401 from verification is an Unauthorized failure and writes nothing`() = runBlocking {
        // Arrange
        val harness = SessionHarness(folder)
        val api = harness.api { jsonReply(CloudflareEnvelopes.failure(code = 1000, message = "Invalid API Token"), HttpStatusCode.Unauthorized) }
        val repository = DefaultSessionRepository(api, harness.active, harness.demoMode, harness.vault, harness.prefs)

        // Act
        val result = repository.signInWithToken("cf-wrong")

        // Assert
        assertTrue(result is ApiResult.Failure && result.error is ApiError.Unauthorized)
        assertNull(harness.vault.read())
    }

    @Test
    fun `accounts are collected across every page`() = runBlocking {
        // Arrange
        val harness = SessionHarness(folder)
        val firstPage = (1..FULL_PAGE).map { "acc-$it" to "Account $it" }
        val api = harness.api { request ->
            when (request.url.encodedPath) {
                "/client/v4/user/tokens/verify" -> jsonReply(envelope("""{"status":"active"}"""))
                "/client/v4/accounts" -> when (request.url.parameters["page"]) {
                    "2" -> jsonReply(envelope(accountsJson(ACCOUNT_B to "Beta"), pageInfo(2, total = FULL_PAGE + 1)))
                    else -> jsonReply(envelope(accountsJson(*firstPage.toTypedArray()), pageInfo(1, total = FULL_PAGE + 1)))
                }
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val repository = DefaultSessionRepository(api, harness.active, harness.demoMode, harness.vault, harness.prefs)

        // Act
        repository.signInWithToken(GOOD_TOKEN)

        // Assert
        val ids = (repository.state.value as SessionState.SignedIn).accounts.map { it.id }
        assertEquals(FULL_PAGE + 1, ids.size)
        assertEquals(ACCOUNT_B, ids.last())
    }

    @Test
    fun `sign out wipes the vault, clears the token and returns to signed out`() = runBlocking {
        // Arrange
        val harness = SessionHarness(folder)
        val api = signedInApi(harness)
        val repository = DefaultSessionRepository(api, harness.active, harness.demoMode, harness.vault, harness.prefs)
        repository.signInWithToken(GOOD_TOKEN)

        // Act
        repository.signOut()

        // Assert
        assertEquals(SessionState.SignedOut, repository.state.value)
        assertNull(harness.vault.read())
        assertNull(harness.active.bearerToken())
        assertFalse(harness.demoMode.enabled.value)
    }

    @Test
    fun `entering the demo flips demo mode and loads the demo accounts from demo routes`() = runBlocking {
        // Arrange
        val harness = SessionHarness(folder)
        harness.demoRoutes.get("/accounts") {
            DemoReply.ok(buildJsonArray { add(buildJsonObject { put("id", "demo-acc"); put("name", "Demo account") }) })
        }
        val api = harness.api { respondError(HttpStatusCode.InternalServerError) }
        val repository = DefaultSessionRepository(api, harness.active, harness.demoMode, harness.vault, harness.prefs)

        // Act
        val result = repository.enterDemo()

        // Assert
        assertTrue(result is ApiResult.Success)
        assertTrue(harness.demoMode.enabled.value)
        val signedIn = repository.state.value as SessionState.SignedIn
        assertTrue(signedIn.isDemo)
        assertEquals("demo-acc", signedIn.selectedAccountId)
        assertEquals(Credential.Demo, harness.vault.read())
    }

    @Test
    fun `restore with a stored token and a live account list signs in with the saved selection`() = runBlocking {
        // Arrange
        val harness = SessionHarness(folder)
        harness.vault.write(Credential.ApiToken(GOOD_TOKEN))
        harness.prefs.writeSelectedAccountId(ACCOUNT_B)
        val api = harness.api { jsonReply(envelope(accountsJson(ACCOUNT_A to "Alpha", ACCOUNT_B to "Beta"))) }
        val repository = DefaultSessionRepository(api, harness.active, harness.demoMode, harness.vault, harness.prefs)

        // Act
        repository.restore()

        // Assert
        val signedIn = repository.state.value as SessionState.SignedIn
        assertEquals(ACCOUNT_B, signedIn.selectedAccountId)
        assertEquals(GOOD_TOKEN, harness.active.bearerToken())
    }

    @Test
    fun `restore with no network falls back to the cached account list`() = runBlocking {
        // Arrange
        val harness = SessionHarness(folder)
        harness.vault.write(Credential.ApiToken(GOOD_TOKEN))
        harness.prefs.writeAccounts(listOf(CloudflareAccount(ACCOUNT_A, "Alpha")))
        val api = harness.api { throw java.io.IOException("offline") }
        val repository = DefaultSessionRepository(api, harness.active, harness.demoMode, harness.vault, harness.prefs)

        // Act
        repository.restore()

        // Assert
        val signedIn = repository.state.value as SessionState.SignedIn
        assertEquals(listOf(ACCOUNT_A), signedIn.accounts.map { it.id })
    }

    @Test
    fun `restore with a revoked token wipes the vault and shows signed out`() = runBlocking {
        // Arrange
        val harness = SessionHarness(folder)
        harness.vault.write(Credential.ApiToken("cf-revoked"))
        val api = harness.api { jsonReply(CloudflareEnvelopes.failure(code = 1000, message = "Invalid"), HttpStatusCode.Unauthorized) }
        val repository = DefaultSessionRepository(api, harness.active, harness.demoMode, harness.vault, harness.prefs)

        // Act
        repository.restore()

        // Assert
        assertEquals(SessionState.SignedOut, repository.state.value)
        assertNull(harness.vault.read())
    }

    @Test
    fun `restore with nothing stored is signed out and makes no request`() = runBlocking {
        // Arrange
        var requests = 0
        val harness = SessionHarness(folder, live = { requests++ })
        val repository = DefaultSessionRepository(harness.api { error("no call expected") }, harness.active, harness.demoMode, harness.vault, harness.prefs)

        // Act
        repository.restore()

        // Assert
        assertEquals(SessionState.SignedOut, repository.state.value)
        assertEquals(0, requests)
    }

    @Test
    fun `restore of a stored demo re-enables demo mode without touching the network`() = runBlocking {
        // Arrange
        val harness = SessionHarness(folder)
        harness.vault.write(Credential.Demo)
        harness.demoRoutes.get("/accounts") { DemoReply.ok(JsonArray(emptyList())) }
        val repository = DefaultSessionRepository(harness.api { error("no live call") }, harness.active, harness.demoMode, harness.vault, harness.prefs)

        // Act
        repository.restore()

        // Assert
        assertTrue(harness.demoMode.enabled.value)
        assertTrue((repository.state.value as SessionState.SignedIn).isDemo)
    }

    @Test
    fun `selecting an account persists it for the next launch`() = runBlocking {
        // Arrange
        val harness = SessionHarness(folder)
        val api = signedInApi(harness, accounts = accountsJson(ACCOUNT_A to "Alpha", ACCOUNT_B to "Beta"))
        val first = DefaultSessionRepository(api, harness.active, harness.demoMode, harness.vault, harness.prefs)
        first.signInWithToken(GOOD_TOKEN)

        // Act
        first.selectAccount(ACCOUNT_B)
        val second = DefaultSessionRepository(api, harness.active, harness.demoMode, harness.vault, harness.prefs)
        second.restore()

        // Assert
        assertEquals(ACCOUNT_B, (second.state.value as SessionState.SignedIn).selectedAccountId)
    }

    private fun signedInApi(harness: SessionHarness, accounts: String = accountsJson(ACCOUNT_A to "Alpha")) =
        harness.api { request ->
            when (request.url.encodedPath) {
                "/client/v4/user/tokens/verify" -> jsonReply(envelope("""{"status":"active"}"""))
                else -> jsonReply(envelope(accounts))
            }
        }

    private fun pageInfo(page: Int, total: Int): String =
        """{"page":$page,"per_page":$FULL_PAGE,"count":1,"total_count":$total}"""

    private fun accountsJson(vararg accounts: Pair<String, String>): String =
        buildJsonArray {
            accounts.forEach { (id, name) -> add(buildJsonObject { put("id", id); put("name", name) }) }
        }.toString()
}
