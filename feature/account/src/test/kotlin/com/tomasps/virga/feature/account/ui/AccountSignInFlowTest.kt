package com.tomasps.virga.feature.account.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.tomasps.virga.core.auth.session.SessionRepository
import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.navigation.SignIn
import com.tomasps.virga.core.testing.DemoFlow
import com.tomasps.virga.feature.account.testing.FakeSessionRepository
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val EMPTY_TITLE = "Paste a token first"
private const val REJECTED_TITLE = "Cloudflare did not accept this token"
private const val NETWORK_TITLE = "No connection"
private const val VERIFY = "Verify and sign in"
private const val TOKEN_LABEL = "API token"
private const val TOKEN_PAGE = "https://dash.cloudflare.com/profile/api-tokens"

/** Sign-in in the account feature: empty, rejected, offline and successful tokens, and the demo entry. */
@RunWith(RobolectricTestRunner::class)
class AccountSignInFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private val session = FakeSessionRepository()
    private var flow: DemoFlow? = null

    @After
    fun tearDown() {
        flow?.close()
    }

    /** A compose rule takes one composition per test, so each test starts its own flow once. */
    /** Guidance sits below the fold of the sign-in column, so each line is scrolled into view before it is checked. */
    private fun scrolledTo(text: String) {
        compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()
    }

    private fun startSignIn(backing: SessionRepository = session): DemoFlow {
        val started = accountFlow(compose, session = backing)
        flow = started
        started.show(SignIn)
        return started
    }

    @Test
    fun `verify with an empty field asks for a token and sends nothing`() {
        val flow = startSignIn()

        flow.clickText(VERIFY)

        flow.assertTextShown(EMPTY_TITLE)
        assertTrue(session.signInTokens.isEmpty())
    }

    @Test
    fun `verify with only spaces is treated as empty`() {
        val flow = startSignIn()
        flow.typeIntoField(TOKEN_LABEL, "     ")

        flow.clickText(VERIFY)

        flow.assertTextShown(EMPTY_TITLE)
        assertTrue(session.signInTokens.isEmpty())
    }

    @Test
    fun `a token is trimmed before it is sent, and the field clears after success`() {
        val flow = startSignIn()
        flow.typeIntoField(TOKEN_LABEL, "  cfut_example_token  ")

        flow.clickText(VERIFY)

        flow.waitUntil { session.signInTokens.isNotEmpty() }
        assertEquals(listOf("cfut_example_token"), session.signInTokens)
        flow.clickText(VERIFY)
        flow.assertTextShown(EMPTY_TITLE)
    }

    @Test
    fun `a token Cloudflare rejects shows the rejected message and stays on sign-in`() {
        session.signInResult = ApiResult.Failure(ApiError.Unauthorized())
        val flow = startSignIn()
        flow.typeIntoField(TOKEN_LABEL, "not-a-real-token")

        flow.clickText(VERIFY)

        flow.assertTextShown(REJECTED_TITLE)
        flow.assertTextShown("Check that the token was copied in full, is active, and has the permissions Virga needs.")
        assertTrue("Sign-in should not navigate after a rejected token: ${flow.history}", flow.history.isEmpty())
    }

    @Test
    fun `a network failure during verify shows the connection message`() {
        session.signInResult = ApiResult.Failure(ApiError.Network("timeout"))
        val flow = startSignIn()
        flow.typeIntoField(TOKEN_LABEL, "cfut_example_token")

        flow.clickText(VERIFY)

        flow.assertTextShown(NETWORK_TITLE)
    }

    @Test
    fun `typing again clears the error`() {
        session.signInResult = ApiResult.Failure(ApiError.Unauthorized())
        val flow = startSignIn()
        flow.typeIntoField(TOKEN_LABEL, "not-a-real-token")
        flow.clickText(VERIFY)
        flow.assertTextShown(REJECTED_TITLE)

        flow.typeIntoField(TOKEN_LABEL, "cfut_second_try")

        flow.waitUntilTextGone(REJECTED_TITLE)
    }

    @Test
    fun `verify is disabled while the token is checked and enabled again after the answer`() {
        val gate = CompletableDeferred<ApiResult<Unit>>()
        val slow = object : SessionRepository by session {
            override suspend fun signInWithToken(token: String): ApiResult<Unit> = gate.await()
        }
        val flow = startSignIn(backing = slow)
        flow.typeIntoField(TOKEN_LABEL, "cfut_example_token")

        flow.clickText(VERIFY)

        compose.onNodeWithText(VERIFY).assertIsNotEnabled()
        gate.complete(ApiResult.Failure(ApiError.Unauthorized()))
        flow.assertTextShown(REJECTED_TITLE)
        compose.onNodeWithText(VERIFY).assertIsEnabled()
    }

    @Test
    fun `explore the demo calls the demo entry and sends no token`() {
        val flow = startSignIn()

        flow.clickText("Explore the demo")

        flow.waitUntil { session.demoEntries == 1 }
        assertEquals(1, session.demoEntries)
        assertTrue(session.signInTokens.isEmpty())
    }

    @Test
    fun `a failed demo entry shows the connection message`() {
        session.demoResult = ApiResult.Failure(ApiError.Network("offline"))
        val flow = startSignIn()

        flow.clickText("Explore the demo")

        flow.assertTextShown(NETWORK_TITLE)
    }

    @Test
    fun `the eye button reveals the token and hides it again`() {
        startSignIn()

        compose.onNodeWithContentDescription("Show token").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Hide token").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Show token").assertIsDisplayed()
    }

    @Test
    fun `guidance lists full and read-only permissions and the Builds and R2 notes`() {
        val flow = startSignIn()

        flow.clickText("How to create a token")

        scrolledTo("Permissions for full use")
        scrolledTo("• Workers CI: Edit (Workers Builds: builds, triggers, build settings, cancel)")
        scrolledTo("Read-only variant: view everything, change nothing")
        scrolledTo("• Account Settings: Read")
        scrolledTo(
            "Workers Builds needs a user token. Create it under My Profile, then API Tokens. " +
                "An account-owned token is refused by the Builds API (\"Invalid token\").",
        )
        scrolledTo(
            "R2 object browsing, upload and delete need an R2 API token with Admin Read and Write, made in the R2 dashboard. " +
                "The permissions above cover buckets and settings only.",
        )
    }

    @Test
    fun `the guidance toggle collapses the permission list again`() {
        val flow = startSignIn()
        flow.clickText("How to create a token")
        flow.assertTextShown("Permissions for full use")

        flow.clickText("How to create a token")

        flow.waitUntilTextGone("Permissions for full use")
    }

    @Test
    fun `the open-page button opens the Cloudflare token page`() {
        val flow = startSignIn()
        flow.clickText("How to create a token")

        flow.clickText("Open the API tokens page")

        assertEquals(listOf(TOKEN_PAGE), flow.openedUrls)
    }
}
