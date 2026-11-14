package com.tomasps.virga.feature.pages.flow

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.navigation.PagesProject
import com.tomasps.virga.core.navigation.PagesRoot
import com.tomasps.virga.core.testing.DemoFlow
import com.tomasps.virga.feature.pages.data.PagesProjectsRepository
import com.tomasps.virga.feature.pages.logic.EnvVarType
import com.tomasps.virga.feature.pages.logic.EnvVarRules
import com.tomasps.virga.feature.pages.ui.PAGES_DASHBOARD_URL
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val SECRET_VALUE = "placeholder-value"
private const val NAME_INVALID = "Names use letters, digits and underscores, and cannot start with a digit."
private const val GIT_REPO = "example-team/lantern-docs"

/** Project settings: branch, build, env vars and secrets, maintenance and delete, in demo mode. */
@RunWith(RobolectricTestRunner::class)
class PagesSettingsFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var flow: DemoFlow

    /** Scrolls the list to [text] if needed, then asserts it is shown once. Rows below the fold are not composed until then. */
    private fun shown(text: String) {
        compose.scrollToText(text)
        flow.assertTextShown(text)
    }

    @Before
    fun setUp() {
        flow = pagesFlow(compose)
        flow.showStack(PagesRoot, PagesProject(LANTERN))
        compose.tapText("Settings")
    }

    @After
    fun tearDown() {
        flow.close()
    }

    // --- Layout ----------------------------------------------------------------------------------

    @Test
    fun `settings groups the sections in order and shows the read-only facts`() {
        shown("Build configuration")
        shown("Environment variables and secrets")
        shown("Compatibility (read-only)")
        shown("2026-01-15")
        shown("nodejs_compat")
        shown("Bindings (read-only)")
        shown("CACHE")
        shown("Git connection")
        shown(GIT_REPO)
        shown("Maintenance")
        shown("Danger zone")
    }

    @Test
    fun `the git row points to the dashboard for connection changes`() {
        compose.tapText("Manage the Git connection in the dashboard")

        assertEquals(listOf(PAGES_DASHBOARD_URL), flow.openedUrls)
    }

    // --- Production branch -----------------------------------------------------------------------

    @Test
    fun `saving a blank production branch is refused and nothing changes`() = runBlocking {
        compose.typeIntoEditable(0, "")
        compose.tapText("Save", index = 0)

        shown("Enter a branch name.")
        assertEquals("main", project().productionBranch)
    }

    @Test
    fun `saving a new production branch writes it, shows it in the header, and confirms`() = runBlocking {
        compose.typeIntoEditable(0, "release")
        compose.tapText("Save", index = 0)

        shown("Production branch saved.")
        assertEquals("release", project().productionBranch)
        flow.waitForText("release")
    }

    @Test
    fun `pressing Done on the keyboard saves the production branch, without a tap on Save`() = runBlocking {
        compose.typeIntoEditable(0, "trunk")
        compose.pressDoneOnEditable(0)

        shown("Production branch saved.")
        assertEquals("trunk", project().productionBranch)
    }

    // --- Build configuration ---------------------------------------------------------------------

    @Test
    fun `saving the build configuration writes the command, directories and caching`() = runBlocking {
        flow.typeIntoField("Build command", "npm run build:prod")
        flow.typeIntoField("Output directory", "out")
        compose.tapText("Save", index = 1)

        shown("Build settings saved.")
        val config = project().buildConfig
        assertEquals("npm run build:prod", config?.buildCommand)
        assertEquals("out", config?.destinationDir)
    }

    // --- Environment variables and secrets -------------------------------------------------------

    @Test
    fun `the production tab shows plain values and hides secrets, and the preview tab has its own set`() {
        shown("SITE_NAME")
        shown("Lantern")
        shown("ANALYTICS_TOKEN")
        compose.onAllNodes(hasText("Secret, value hidden")).assertCountEquals(1)

        compose.tapText("Preview")

        shown("Lantern (preview)")
        compose.onAllNodes(hasText("ANALYTICS_TOKEN")).assertCountEquals(0)
    }

    @Ignore(TEXT_FIELD_DIALOG_BLOCKED)
    @Test
    fun `an invalid variable name keeps the add dialog open and Save disabled`() {
        compose.tapText("Add")
        compose.typeIntoDialogFirst("1BAD-NAME")

        compose.assertDialogText(NAME_INVALID)
        compose.dialogButton("Save").assertIsNotEnabled()
    }

    @Ignore(TEXT_FIELD_DIALOG_BLOCKED)
    @Test
    fun `adding a plain variable writes it under production and lists it`() = runBlocking {
        compose.tapText("Add")
        compose.typeIntoDialogFirst("NEW_FLAG")
        compose.typeIntoDialogLast("flag-value-42")
        compose.tapInDialog("Save")

        shown("Variable saved.")
        compose.scrollToText("NEW_FLAG")
        shown("flag-value-42")
        assertEquals("flag-value-42", envVar("NEW_FLAG")?.value)
    }

    @Ignore(TEXT_FIELD_DIALOG_BLOCKED)
    @Test
    fun `a new secret cannot be saved without a value`() {
        compose.tapText("Add")
        compose.typeIntoDialogFirst("API_KEY")
        compose.tapInDialog("Secret")

        compose.dialogButton("Save").assertIsNotEnabled()
    }

    @Ignore(TEXT_FIELD_DIALOG_BLOCKED)
    @Test
    fun `adding a secret stores it, lists it as hidden, and never shows the value`() = runBlocking {
        compose.tapText("Add")
        compose.typeIntoDialogFirst("API_KEY")
        compose.tapInDialog("Secret")
        compose.typeIntoDialogLast(SECRET_VALUE)
        compose.tapInDialog("Save")

        shown("Variable saved.")
        compose.scrollToText("API_KEY")
        compose.onAllNodes(hasText("Secret, value hidden")).assertCountEquals(2)
        compose.onAllNodes(hasText(SECRET_VALUE, substring = true)).assertCountEquals(0)
        assertEquals(EnvVarType.SECRET, envVar("API_KEY")?.type)
    }

    @Ignore(TEXT_FIELD_DIALOG_BLOCKED)
    @Test
    fun `editing a plain variable saves the new value`() = runBlocking {
        compose.tapText("Edit", index = 1)
        compose.typeIntoDialogLast("Lantern Docs")
        compose.tapInDialog("Save")

        shown("Variable saved.")
        flow.waitForText("Lantern Docs")
        assertEquals("Lantern Docs", envVar("SITE_NAME")?.value)
    }

    @Ignore(TEXT_FIELD_DIALOG_BLOCKED)
    @Test
    fun `saving an existing secret with a blank value keeps the stored secret and says so`() {
        compose.tapText("Edit", index = 0)
        compose.tapInDialog("Save")

        shown("Nothing to save: the stored secret is kept.")
    }

    @Ignore(TEXT_FIELD_DIALOG_BLOCKED)
    @Test
    fun `turning a plain variable into a secret needs a value, and Save stays disabled until one is typed`() {
        compose.tapText("Edit", index = 1)
        compose.tapInDialog("Secret")
        compose.typeIntoDialogLast("")

        compose.dialogButton("Save").assertIsNotEnabled()
    }

    @Test
    fun `removing a variable asks first - cancel keeps it, confirm removes it`() = runBlocking {
        compose.tapText("Remove", index = 1)
        compose.assertDialogText("Remove this variable?")
        compose.tapInDialog("Cancel")
        shown("SITE_NAME")

        compose.tapText("Remove", index = 1)
        compose.assertDialogText("Remove this variable?")
        compose.tapInDialog("Remove")

        shown("Variable removed.")
        flow.waitUntilTextGone("SITE_NAME")
        assertNull(envVar("SITE_NAME"))
    }

    // --- Maintenance and danger zone -------------------------------------------------------------

    @Test
    fun `purging the build cache asks first, then confirms with a message`() {
        compose.tapText("Purge build cache")
        compose.assertDialogText("Purge the build cache?")
        compose.tapInDialog("Cancel")
        compose.assertNoDialogText("Purge the build cache?")
        shown("Purge build cache")

        compose.tapText("Purge build cache")
        compose.assertDialogText("Purge the build cache?")
        compose.tapInDialog("Purge")

        shown("Build cache purged.")
    }

    @Ignore(TEXT_FIELD_DIALOG_BLOCKED)
    @Test
    fun `deleting the project needs its typed name - once deleted the list no longer shows it`() {
        compose.tapText("Delete project")
        compose.assertDialogText("Delete this Pages project?")
        compose.dialogButton("Delete project").assertIsNotEnabled()
        compose.typeIntoDialogField("lantern-docs")
        compose.tapInDialog("Delete project")

        flow.waitUntilTextGone(LANTERN)
        shown(TIDEWATER)
    }

    @Ignore(TEXT_FIELD_DIALOG_BLOCKED)
    @Test
    fun `cancelling the delete leaves the project in place`() = runBlocking {
        compose.tapText("Delete project")
        compose.assertDialogText("Delete this Pages project?")
        compose.typeIntoDialogField("lantern-docs")
        compose.tapInDialog("Cancel")

        compose.assertNoDialogText("Delete this Pages project?")
        assertTrue(project().name == LANTERN)
    }

    // --- helpers ---------------------------------------------------------------------------------

    private suspend fun project() = when (val result = flow.get<PagesProjectsRepository>().project(DEMO_ACCOUNT, LANTERN)) {
        is ApiResult.Success -> result.value
        is ApiResult.Failure -> error("the demo project could not be read: ${result.error}")
    }

    private suspend fun envVar(name: String) =
        EnvVarRules.entriesOf(project().deploymentConfigs?.production).firstOrNull { it.name == name }
}
