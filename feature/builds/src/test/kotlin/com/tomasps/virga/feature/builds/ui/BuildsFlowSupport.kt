@file:OptIn(ExperimentalTestApi::class)

package com.tomasps.virga.feature.builds.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.compose.ui.test.waitUntilDoesNotExist
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.demo.DemoRouteContributor
import com.tomasps.virga.core.network.demo.DemoRouteRegistry
import com.tomasps.virga.core.testing.DemoFlow
import com.tomasps.virga.core.testing.FLOW_WAIT_TIMEOUT_MS
import com.tomasps.virga.core.testing.FeatureUnderTest
import com.tomasps.virga.feature.builds.api.BuildRecord
import com.tomasps.virga.feature.builds.api.BuildsRepository
import com.tomasps.virga.feature.builds.buildsKoinModule
import com.tomasps.virga.feature.builds.demo.BuildsDemoRoutes
import com.tomasps.virga.feature.builds.demo.DEMO_LIVE_BUILD_UUID
import com.tomasps.virga.feature.builds.demo.DEMO_LIVE_SCRIPT
import com.tomasps.virga.feature.builds.registerBuildsEntries
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

/** The demo account the builds flows run in, and the Worker whose seeded live build the demo serves. */
const val DEMO_PERSONAL = "demo-account-personal"
const val DEMO_WORKER = DEMO_LIVE_SCRIPT
const val DEMO_HISTORY_NEWEST_UUID = "demo-hist-0006"

/** A fixed start for the demo clock, so the seeded live build stays queued until a test moves time. */
val FLOW_START: Instant = Instant.parse("2026-11-11T10:00:00Z")

/**
 * The demo clock. The demo reads its builds' states from it, so a test moves a build through its stages by moving this
 * clock, not by waiting. Advance the clock and the view model's next poll, with [DemoFlow.advanceClock].
 */
class FlowClock(start: Instant = FLOW_START) {
    private val millis = AtomicLong(start.toEpochMilli())
    fun read(): Long = millis.get()
    fun advance(ms: Long) {
        millis.addAndGet(ms)
    }
}

/**
 * The Workers list route. In the app the Workers feature serves it; the builds flow harness does not include that
 * feature, so this stands in for it with the two demo Worker names.
 */
private class DemoWorkerListRoutes : DemoRouteContributor {
    override fun contribute(registry: DemoRouteRegistry) {
        registry.get(SCRIPTS_PATH) { _ ->
            DemoReply.ok(buildJsonArray {
                listOf(DEMO_WORKER, OTHER_WORKER).forEach { name -> add(buildJsonObject { put("id", name) }) }
            })
        }
    }

    private companion object {
        const val SCRIPTS_PATH = "/accounts/{accountId}/workers/scripts"
    }
}

const val OTHER_WORKER = "api-worker"

/** Demo builds routes with the clock replaced, so a flow controls time. Replaces the default BuildsDemoRoutes binding. */
private fun clockModule(clock: FlowClock): Module = module {
    single { BuildsDemoRoutes(clock::read) } bind DemoRouteContributor::class
}

private val workerListModule: Module = module {
    single { DemoWorkerListRoutes() } bind DemoRouteContributor::class
}

/** The builds feature, in demo mode, with a controllable clock. Use one per test. */
fun buildsDemoFeature(clock: FlowClock = FlowClock()): FeatureUnderTest =
    FeatureUnderTest(listOf(buildsKoinModule, workerListModule, clockModule(clock)), ::registerBuildsEntries)

/** Waits until a node with this content description exists. Status dots carry their label that way. */
fun ComposeContentTestRule.waitForStatus(label: String) {
    waitUntilAtLeastOneExists(hasContentDescription(label), FLOW_WAIT_TIMEOUT_MS)
}

/** Asserts a status dot with [label] is on screen (the first, when several rows share it), read from the unmerged tree. */
fun ComposeContentTestRule.assertStatusShown(label: String) {
    waitForStatus(label)
    onAllNodes(hasContentDescription(label), useUnmergedTree = true).onFirst().assertIsDisplayed()
}

/** Waits for a text node inside a dialog window with exactly [text]. */
fun ComposeContentTestRule.waitForDialogText(text: String) {
    waitUntilAtLeastOneExists(hasText(text) and hasAnyAncestor(isDialog()), FLOW_WAIT_TIMEOUT_MS)
}

fun ComposeContentTestRule.waitForDialogTextGone(text: String) {
    waitUntilDoesNotExist(hasText(text) and hasAnyAncestor(isDialog()), FLOW_WAIT_TIMEOUT_MS)
}

/** Taps the clickable element with [label] inside the open dialog. Needed when the screen behind has the same label. */
fun ComposeContentTestRule.clickInDialog(label: String) {
    val matcher = hasText(label) and hasClickAction() and hasAnyAncestor(isDialog())
    waitUntilAtLeastOneExists(matcher, FLOW_WAIT_TIMEOUT_MS)
    onNode(matcher).performClick()
    waitForIdle()
}

/** Replaces the text of the [index]th text field inside the open dialog, in tree order. */
fun ComposeContentTestRule.typeIntoDialogField(index: Int, text: String) {
    waitUntilAtLeastOneExists(hasSetTextAction() and hasAnyAncestor(isDialog()), FLOW_WAIT_TIMEOUT_MS)
    onAllNodes(hasSetTextAction() and hasAnyAncestor(isDialog()), useUnmergedTree = true)[index]
        .performTextReplacement(text)
    waitForIdle()
}

/** The Worker's builds as the repository reads them, for checks that the demo state changed. */
fun DemoFlow.workerBuilds(): List<BuildRecord> = kotlinx.coroutines.runBlocking {
    val repository = get<BuildsRepository>()
    val tag = (repository.tagFor(DEMO_PERSONAL, DEMO_WORKER) as ApiResult.Success).value
    (repository.builds(DEMO_PERSONAL, tag, DEMO_WORKER, page = 1, perPage = 100) as ApiResult.Success).value
}

/** The seeded live build as the repository reads it right now. */
fun DemoFlow.liveBuild(): BuildRecord = kotlinx.coroutines.runBlocking {
    val repository = get<BuildsRepository>()
    (repository.build(DEMO_PERSONAL, DEMO_LIVE_BUILD_UUID, DEMO_WORKER) as ApiResult.Success).value
}

/** A Worker's tag through the same repository the screens use. */
fun DemoFlow.tagOf(scriptName: String): String = kotlinx.coroutines.runBlocking {
    (get<BuildsRepository>().tagFor(DEMO_PERSONAL, scriptName) as ApiResult.Success).value
}
