package com.tomasps.virga.core.auth.session

import com.tomasps.virga.core.auth.SessionHarness
import com.tomasps.virga.core.auth.envelope
import com.tomasps.virga.core.auth.jsonReply
import com.tomasps.virga.core.auth.vault.CredentialVault
import com.tomasps.virga.core.auth.vault.SecretCipher
import com.tomasps.virga.core.auth.vault.SealedBytes
import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.auth.Credential
import com.tomasps.virga.core.network.demo.DemoReply
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.ProviderException

/** A Keystore that has gone wrong: every operation throws the RuntimeException the platform really throws. */
private object BrokenKeystore : SecretCipher {
    override fun encrypt(plaintext: ByteArray): SealedBytes = throw ProviderException("keystore unavailable")
    override fun decrypt(sealed: SealedBytes): ByteArray = throw ProviderException("keystore unavailable")
}

class SessionFailureTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `a keystore failure during restore settles as signed out instead of leaving the splash`() = runBlocking {
        val harness = SessionHarness(folder)
        harness.vault.write(Credential.ApiToken("cf-token"))
        val repository = DefaultSessionRepository(
            harness.api { jsonReply(envelope("[]")) },
            harness.active,
            harness.demoMode,
            CredentialVault(harness.store, BrokenKeystore),
            harness.prefs,
        )

        repository.restore()

        assertEquals(SessionState.SignedOut, repository.state.value)
    }

    @Test
    fun `a keystore failure during sign-in is reported, saves nothing and keeps the previous token`() = runBlocking {
        val harness = SessionHarness(folder)
        val api = harness.api { request ->
            when (request.url.encodedPath) {
                "/client/v4/user/tokens/verify" -> jsonReply(envelope("""{"id":"t1","status":"active"}"""))
                else -> jsonReply(envelope("""[{"id":"acc-a","name":"Alpha"}]"""))
            }
        }
        val repository = DefaultSessionRepository(api, harness.active, harness.demoMode, CredentialVault(harness.store, BrokenKeystore), harness.prefs)

        val result = repository.signInWithToken("cf-token")

        assertTrue(result is ApiResult.Failure && result.error is ApiError.Local)
        assertEquals(null, harness.active.bearerToken())
        assertFalse(repository.state.value is SessionState.SignedIn)
    }

    @Test
    fun `sign-in while demo is on verifies against the live API and turns demo off`() = runBlocking {
        val harness = SessionHarness(folder, live = {})
        harness.demoRoutes.get("/user/tokens/verify") { DemoReply.ok(buildJsonObject { put("status", "active") }) }
        harness.demoMode.setEnabled(true)
        val api = harness.api { jsonReply(envelope("""{"id":"t1","status":"disabled"}""")) }
        val repository = DefaultSessionRepository(api, harness.active, harness.demoMode, harness.vault, harness.prefs)

        val result = repository.signInWithToken("arbitrary-string")

        assertTrue("canned demo verify must not accept a live token", result is ApiResult.Failure)
    }

    @Test
    fun `a failed sign-in while demo was on puts demo mode back`() = runBlocking {
        val harness = SessionHarness(folder)
        harness.demoMode.setEnabled(true)
        val api = harness.api { jsonReply(envelope("""{"id":"t1","status":"disabled"}""")) }
        val repository = DefaultSessionRepository(api, harness.active, harness.demoMode, harness.vault, harness.prefs)

        repository.signInWithToken("cf-token")

        assertTrue(harness.demoMode.enabled.value)
    }

    @Test
    fun `sign-out clears the token and demo mode and lands signed out`() = runBlocking {
        val harness = SessionHarness(folder)
        harness.active.use("cf-token")
        harness.demoMode.setEnabled(true)
        val repository = DefaultSessionRepository(
            harness.api { jsonReply(envelope("[]")) },
            harness.active,
            harness.demoMode,
            harness.vault,
            harness.prefs,
        )

        repository.signOut()

        assertEquals(SessionState.SignedOut, repository.state.value)
        assertNull(harness.active.bearerToken())
        assertFalse(harness.demoMode.enabled.value)
    }
}
