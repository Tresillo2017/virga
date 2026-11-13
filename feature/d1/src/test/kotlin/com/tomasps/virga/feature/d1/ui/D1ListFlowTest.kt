@file:OptIn(ExperimentalTestApi::class)

package com.tomasps.virga.feature.d1.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import com.tomasps.virga.core.testing.FLOW_WAIT_TIMEOUT_MS
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasContentDescription
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.navigation.D1Database
import com.tomasps.virga.core.navigation.D1Root
import com.tomasps.virga.core.testing.DemoFlow
import com.tomasps.virga.core.testing.FeatureUnderTest
import com.tomasps.virga.feature.d1.data.DatabasesRepository
import com.tomasps.virga.feature.d1.d1KoinModule
import com.tomasps.virga.feature.d1.list.RecentlyDeletedDatabase
import com.tomasps.virga.feature.d1.list.RecentlyDeletedLog
import com.tomasps.virga.feature.d1.registerD1Entries
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Screen-level flows for the D1 database list, in demo mode. The flows that confirm a deletion by typing a name are in
 * D1FormsHostTest, because a text field inside a dialog window never settles under Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
class D1ListFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var flow: DemoFlow

    @Before
    fun setUp() {
        flow = DemoFlow(compose, FeatureUnderTest(listOf(d1KoinModule, isolatedD1Modules()), ::registerD1Entries))
    }

    @After
    fun tearDown() {
        flow.close()
    }

    @Test
    fun `the list shows the demo databases`() {
        flow.show(D1Root)

        flow.assertTextShown(ORDERS_NAME)
        flow.assertTextShown(LEDGER_NAME)
    }

    @Test
    fun `a jurisdiction is shown by its readable name, never by its API value`() {
        flow.show(D1Root)

        compose.waitUntil(FLOW_WAIT_TIMEOUT_MS) { compose.onAllNodes(hasText("Jurisdiction: EU", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasText("Jurisdiction: eu", substring = true)).assertCountEquals(0)
    }

    @Test
    fun `tapping a database opens its screen`() {
        flow.show(D1Root)

        flow.clickText(ORDERS_NAME)

        flow.assertNavigatedTo(D1Database(ORDERS_ID))
    }

    @Test
    fun `an empty account shows the empty state and the create action still works`() {
        deleteInStore(ORDERS_ID)
        deleteInStore(LEDGER_ID)
        flow.show(D1Root)

        flow.assertTextShown("No databases yet")
        compose.onAllNodes(hasContentDescription("Create database") and hasClickAction()).assertCountEquals(1)
    }

    @Test
    fun `a recently deleted database is offered for restore with a readable deadline, and restore brings it back`() {
        deleteInStore(ORDERS_ID)
        rememberDeleted(ORDERS_ID, ORDERS_NAME)
        flow.show(D1Root)

        flow.waitForText("Recently deleted")
        assertNoIsoDateShown()
        flow.clickText("Restore")

        flow.waitUntilTextGone("Recently deleted")
        assertTrue(listedDatabases(flow.get()).any { it.name == ORDERS_NAME })
    }

    @Test
    fun `a database deleted from its own screen is not offered as a live row when the list is shown again`() {
        flow.show(D1Root)
        flow.clickText(ORDERS_NAME)
        deleteInStore(ORDERS_ID)
        rememberDeleted(ORDERS_ID, ORDERS_NAME)

        compose.runOnUiThread { flow.navigator.back() }
        compose.waitForIdle()

        flow.waitForText(LEDGER_NAME)
        compose.onAllNodes(hasText(ORDERS_NAME) and hasClickAction()).assertCountEquals(0)
    }

    private fun assertNoIsoDateShown() {
        val texts = compose.onAllNodes(hasText("Restore until", substring = true)).fetchSemanticsNodes()
            .flatMap { node -> node.config[SemanticsProperties.Text].map { it.text } }
        assertTrue("Expected a readable deadline in $texts", texts.isNotEmpty())
        assertTrue("Deadline must not be an ISO date: $texts", texts.none { ISO_DATE.containsMatchIn(it) })
    }

    private fun deleteInStore(databaseId: String) = runBlocking {
        val result = flow.get<DatabasesRepository>().delete(D1_DEMO_ACCOUNT, databaseId)
        check(result is ApiResult.Success) { "demo delete failed: $result" }
    }

    private fun rememberDeleted(databaseId: String, name: String) = runBlocking {
        val now = System.currentTimeMillis()
        flow.get<RecentlyDeletedLog>().remember(D1_DEMO_ACCOUNT, RecentlyDeletedDatabase(databaseId, name, now), now)
    }

    private companion object {
        val ISO_DATE = Regex("""\d{4}-\d{2}-\d{2}""")
        const val LEDGER_ID = "demo-db-ledger"
    }
}
