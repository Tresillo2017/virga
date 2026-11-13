package com.tomasps.virga.feature.kv.ui

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.tomasps.virga.core.auth.session.CloudflareAccount
import com.tomasps.virga.core.navigation.KvNamespace
import com.tomasps.virga.core.navigation.KvRoot
import com.tomasps.virga.core.testing.DEFAULT_DEMO_ACCOUNTS
import com.tomasps.virga.core.testing.DemoFlow
import com.tomasps.virga.feature.kv.demo.KV_DEMO_ACCOUNT_PERSONAL
import com.tomasps.virga.feature.kv.demo.KV_DEMO_NS_FLAGS
import com.tomasps.virga.feature.kv.demo.KvDemoStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val CREATE_DESCRIPTION = "Create namespace"
private const val NEW_TITLE = "Build cache"
private const val EMPTY_TITLE = "No namespaces"
private const val EMPTY_MESSAGE = "Create a namespace with the + button."

/** The KV namespaces list in demo mode: the list, the empty and error states, and creating a namespace. */
@RunWith(RobolectricTestRunner::class)
class KvNamespacesFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var flow: DemoFlow

    @Before
    fun setUp() {
        quickIdleTimeout()
        flow = kvDemoFlow(compose)
    }

    @After
    fun tearDown() {
        flow.close()
        restoreIdleTimeout()
    }

    private fun store(): KvDemoStore = flow.get<KvDemoStore>()

    private fun personalNamespaceTitles(): List<String> =
        store().namespaces.items.value.filterKeys { it.startsWith(KV_DEMO_ACCOUNT_PERSONAL) }.values.map { it.title }

    @Test
    fun `tapping a namespace opens it`() {
        flow.showSettled(KvRoot)

        flow.clickText("Feature flags")

        flow.assertNavigatedTo(KvNamespace(KV_DEMO_NS_FLAGS))
    }

    @Test
    fun `an account with no namespaces shows the empty state with the create hint`() {
        store().namespaces.items.value.keys.filter { it.startsWith(KV_DEMO_ACCOUNT_PERSONAL) }
            .forEach { store().namespaces.remove(it) }

        flow.showSettled(KvRoot)

        flow.assertTextShown(EMPTY_TITLE)
        flow.assertTextShown(EMPTY_MESSAGE)
    }

    @Test
    fun `an account the demo cannot see shows the not-found error`() {
        val other = CloudflareAccount(id = "not-a-demo-account", name = "Other")
        flow.close()
        flow = DemoFlow(compose, kvFeatureUnderTest(), accounts = DEFAULT_DEMO_ACCOUNTS + other, selectedAccountId = other.id)

        flow.showSettled(KvRoot)

        flow.assertTextShown("Not found")
        flow.assertTextShown("That item no longer exists, or this token cannot see it.")
    }

    @Ignore(DIALOG_IDLE_LIMIT)
    @Test
    fun `create namespace needs a title, and Create is off while the title is blank`() {
        flow.showSettled(KvRoot)
        compose.onNodeWithContentDescription(CREATE_DESCRIPTION).performClick()
        flow.waitForText("New namespace")

        compose.onNodeWithText("Create").assertIsNotEnabled()
    }

    @Ignore(DIALOG_IDLE_LIMIT)
    @Test
    fun `cancel on the create dialog creates nothing`() {
        val before = personalNamespaceTitles().size
        flow.showSettled(KvRoot)
        compose.onNodeWithContentDescription(CREATE_DESCRIPTION).performClick()
        flow.typeIntoField("Title", NEW_TITLE)

        flow.clickText("Cancel")

        flow.waitUntilTextGone("New namespace")
        assertEquals(before, personalNamespaceTitles().size)
    }

    @Ignore(DIALOG_IDLE_LIMIT)
    @Test
    fun `creating a namespace with a title lists it and says so`() {
        flow.showSettled(KvRoot)
        compose.onNodeWithContentDescription(CREATE_DESCRIPTION).performClick()
        flow.typeIntoField("Title", "  $NEW_TITLE  ")

        flow.clickText("Create")

        flow.waitForText("Created namespace $NEW_TITLE.")
        flow.assertTextShown(NEW_TITLE)
        assertTrue(personalNamespaceTitles().contains(NEW_TITLE))
    }

    @Ignore(DIALOG_IDLE_LIMIT)
    @Test
    fun `a data location can be picked before creating, and it does not block the create`() {
        flow.showSettled(KvRoot)
        compose.onNodeWithContentDescription(CREATE_DESCRIPTION).performClick()
        flow.typeIntoField("Title", "EU cache")

        flow.clickText("European Union (eu)")
        flow.clickText("Create")

        flow.waitForText("Created namespace EU cache.")
    }
}
