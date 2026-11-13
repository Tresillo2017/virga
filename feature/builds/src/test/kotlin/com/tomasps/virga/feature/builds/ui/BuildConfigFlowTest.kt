package com.tomasps.virga.feature.builds.ui

import androidx.compose.ui.test.junit4.createComposeRule
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.testing.DemoFlow
import com.tomasps.virga.feature.builds.BuildConfigScreen
import com.tomasps.virga.feature.builds.api.BuildsRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Dialog fields in tree order: production branch, build command, deploy command, root directory.
private const val FIELD_BRANCH = 0
private const val FIELD_BUILD_COMMAND = 1

/** The build settings screen in demo mode: display, the Edit dialog, saving, and the checks before a save. */
@RunWith(RobolectricTestRunner::class)
class BuildConfigFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var flow: DemoFlow
    private val screen = BuildConfigScreen(DEMO_PERSONAL, DEMO_WORKER)

    @Before
    fun setUp() {
        flow = DemoFlow(compose, buildsDemoFeature(FlowClock()))
    }

    @After
    fun tearDown() {
        flow.close()
    }

    @Test
    fun `settings show the repository, the build commands and the previews`() {
        flow.show(screen)

        flow.assertTextShown("demo-owner")
        flow.assertTextShown("npm run build")
        flow.assertTextShown("npx wrangler deploy")
        flow.assertTextShown("feature/search")
        flow.assertTextShown("Auto build on")
    }

    @Test
    fun `editing a build command saves it, confirms the save, and shows the new value`() {
        flow.show(screen)

        flow.clickText("Edit")
        compose.waitForDialogText("Edit build settings")
        compose.typeIntoDialogField(FIELD_BUILD_COMMAND, "npm run build:prod")
        compose.clickInDialog("Save")

        compose.waitForDialogTextGone("Edit build settings")
        flow.waitForText("Build settings saved.")
        flow.assertTextShown("npm run build:prod")
        assertEquals("npm run build:prod", buildCommand())
    }

    @Test
    fun `reopening Edit after a save shows the form again`() {
        flow.show(screen)
        flow.clickText("Edit")
        compose.waitForDialogText("Edit build settings")
        compose.typeIntoDialogField(FIELD_BUILD_COMMAND, "npm run build:next")
        compose.clickInDialog("Save")
        compose.waitForDialogTextGone("Edit build settings")

        flow.clickText("Edit")

        compose.waitForDialogText("Edit build settings")
        compose.clickInDialog("Cancel")
        compose.waitForDialogTextGone("Edit build settings")
    }

    @Test
    fun `a blank production branch is refused in the form and nothing is sent`() {
        flow.show(screen)
        val branchBefore = productionBranch()

        flow.clickText("Edit")
        compose.waitForDialogText("Edit build settings")
        compose.typeIntoDialogField(FIELD_BRANCH, "   ")
        compose.clickInDialog("Save")

        compose.waitForDialogText("Enter a production branch.")
        compose.waitForDialogText("Edit build settings")
        assertEquals(branchBefore, productionBranch())
    }

    @Test
    fun `cancelling the edit leaves the settings as they were`() {
        flow.show(screen)

        flow.clickText("Edit")
        compose.waitForDialogText("Edit build settings")
        compose.typeIntoDialogField(FIELD_BUILD_COMMAND, "make")
        compose.clickInDialog("Cancel")

        compose.waitForDialogTextGone("Edit build settings")
        flow.assertTextShown("npm run build")
        assertEquals("npm run build", buildCommand())
    }

    @Test
    fun `a changed production branch is saved and shown`() {
        flow.show(screen)

        flow.clickText("Edit")
        compose.waitForDialogText("Edit build settings")
        compose.typeIntoDialogField(FIELD_BRANCH, "release")
        compose.clickInDialog("Save")

        compose.waitForDialogTextGone("Edit build settings")
        flow.waitForText("release")
        assertEquals("release", productionBranch())
    }

    private fun buildCommand(): String? = readConfig().buildCommand

    private fun productionBranch(): String? = readConfig().productionBranch

    private fun readConfig() = runBlocking {
        val repository = flow.get<BuildsRepository>()
        (repository.config(DEMO_PERSONAL, flow.tagOf(DEMO_WORKER)) as ApiResult.Success).value
    }
}
