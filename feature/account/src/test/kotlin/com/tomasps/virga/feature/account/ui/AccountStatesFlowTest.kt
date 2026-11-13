package com.tomasps.virga.feature.account.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.tomasps.virga.core.auth.session.SessionState
import com.tomasps.virga.core.network.CloudflareApi
import com.tomasps.virga.core.navigation.AccountRoot
import com.tomasps.virga.core.testing.DemoFlow
import com.tomasps.virga.core.testing.FLOW_WAIT_TIMEOUT_MS
import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.testing.mockCloudflareApi
import com.tomasps.virga.feature.account.AccountSwitcher
import com.tomasps.virga.feature.account.details.AccountDetailsRepository
import com.tomasps.virga.feature.account.testing.FakeSessionRepository
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner

private const val LOADING = "Loading account details"
private const val NETWORK_TITLE = "No connection"
private const val RETRY = "Retry"

/** Loading, failure, retry and no-account states on the account tab and switcher. */
@RunWith(RobolectricTestRunner::class)
class AccountStatesFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private var flow: DemoFlow? = null

    @After
    fun tearDown() {
        flow?.close()
    }

    @Test
    fun `details that are still loading show a loading indicator and keep the account rows on screen`() {
        val release = CompletableDeferred<Unit>()
        val slowApi = mockCloudflareApi {
            get("/accounts/{accountId}") { request ->
                release.await()
                DemoReply.ok(detailsJson(request.pathParams["accountId"].orEmpty()))
            }
        }
        val started = accountFlow(compose, extra = listOf(detailsOverride(slowApi)))
        flow = started

        started.show(AccountRoot)

        compose.onNodeWithContentDescription(LOADING).assertIsDisplayed()
        compose.onNodeWithText("Switch account").assertIsDisplayed()
        compose.onNodeWithText("Settings").assertIsDisplayed()
        compose.onNodeWithText("About Virga").assertIsDisplayed()
        release.complete(Unit)
    }

    @Test
    fun `details that fail show a friendly message and retry loads them again`() {
        var calls = 0
        val flakyApi = mockCloudflareApi {
            get("/accounts/{accountId}") { request ->
                calls++
                if (calls == 1) {
                    DemoReply.failure(HttpStatusCode.InternalServerError, code = 1000, message = "boom")
                } else {
                    DemoReply.ok(detailsJson(request.pathParams["accountId"].orEmpty()))
                }
            }
        }
        val started = accountFlow(compose, extra = listOf(detailsOverride(flakyApi)))
        flow = started

        started.show(AccountRoot)

        started.assertTextShown("Cloudflare is having trouble")
        started.clickText(RETRY)
        started.assertTextShown("demo-account-personal")
        assertEquals(2, calls)
    }

    @Test
    fun `details that cannot reach Cloudflare show the connection message`() {
        val offlineApi = mockCloudflareApi {
            get("/accounts/{accountId}") { _ -> throw java.io.IOException("unreachable") }
        }
        val started = accountFlow(compose, extra = listOf(detailsOverride(offlineApi)))
        flow = started

        started.show(AccountRoot)

        started.assertTextShown(NETWORK_TITLE)
        started.assertTextShown(RETRY)
    }

    @Test
    fun `a signed-in session with no accounts says so on the account tab`() {
        val started = accountFlow(compose, session = noAccountsSession())
        flow = started

        started.show(AccountRoot)

        started.assertTextShown("No account selected")
        assertTrue(compose.onAllNodes(hasText("0 accounts")).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `a signed-in session with no accounts shows an empty switcher`() {
        val started = accountFlow(compose, session = noAccountsSession())
        flow = started

        started.show(AccountSwitcher)

        started.assertTextShown("No accounts")
        started.assertTextShown("This token cannot see any Cloudflare accounts.")
    }

    private fun noAccountsSession() = FakeSessionRepository(
        initial = SessionState.SignedIn(accounts = emptyList(), selectedAccountId = null, isDemo = false),
    )

    @Test
    fun `after switching accounts the account tab shows the new account and not the old one`() {
        val started = accountFlow(compose)
        flow = started

        started.show(AccountRoot)
        started.assertTextShown("demo-account-personal")
        started.clickText("Switch account")
        started.clickText("Staging (demo)")

        // The switcher also lists account ids, so wait until it is gone: the tab is back and shows the new account.
        compose.waitUntil(FLOW_WAIT_TIMEOUT_MS) {
            compose.onAllNodes(hasContentDescription("Selected")).fetchSemanticsNodes().isEmpty()
        }
        started.assertTextShown("Staging (demo)")
        started.assertTextShown("demo-account-staging")
        assertEquals(0, compose.onAllNodes(hasText("demo-account-personal")).fetchSemanticsNodes().size)
    }

    private fun detailsOverride(api: CloudflareApi) =
        module { single { AccountDetailsRepository(api) } }

    private fun detailsJson(accountId: String) = buildJsonObject {
        put("id", accountId)
        put("name", "Name of $accountId")
        put("type", "standard")
        put("created_on", "2024-05-01T09:00:00Z")
    }
}
