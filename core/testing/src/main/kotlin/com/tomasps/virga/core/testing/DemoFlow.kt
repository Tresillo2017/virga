@file:OptIn(ExperimentalTestApi::class)

package com.tomasps.virga.core.testing

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.tomasps.virga.core.auth.session.CloudflareAccount
import com.tomasps.virga.core.navigation.LocalNavigator
import com.tomasps.virga.core.navigation.Navigator
import com.tomasps.virga.core.navigation.RouteRegistry
import com.tomasps.virga.core.navigation.registerSharedKeys
import com.tomasps.virga.core.ui.theme.VirgaTheme
import org.koin.compose.KoinIsolatedContext
import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

/** How long a wait polls before it fails. Generous on purpose: a Robolectric frame costs real time. */
const val FLOW_WAIT_TIMEOUT_MS = 10_000L

/** The feature under test: its own Koin modules and its real register function from its feature module. */
class FeatureUnderTest(
    val koinModules: List<Module>,
    val registerEntries: (RouteRegistry) -> Unit,
)

/**
 * One UI flow for one feature, in demo mode, with the feature's real Koin graph and real registration.
 *
 * Use it from a test that owns a `createComposeRule()`:
 *
 * ```
 * @get:Rule val compose = createComposeRule()
 * private lateinit var flow: DemoFlow
 *
 * @Before fun setUp() { flow = DemoFlow(compose, FeatureUnderTest(listOf(kvKoinModule), ::registerKvEntries)) }
 * @After fun tearDown() = flow.close()
 * ```
 *
 * Call [showStack] once per test: a compose rule accepts one `setContent`. The last key is the screen shown.
 */
class DemoFlow(
    private val compose: ComposeContentTestRule,
    feature: FeatureUnderTest,
    private val darkTheme: Boolean = true,
    accounts: List<CloudflareAccount> = DEFAULT_DEMO_ACCOUNTS,
    selectedAccountId: String = accounts.first().id,
) : AutoCloseable {

    /** The app's Koin graph for this feature, started in demo mode. Read a bean with [get]. */
    private val koinApp: KoinApplication = koinApplication {
        modules(flowKoinModules(context(), accounts, selectedAccountId, feature.koinModules))
    }

    val koin: Koin = koinApp.koin

    private val live: SnapshotStateList<NavKey> = mutableStateListOf()
    private val recording = RecordingBackStack(live)
    private val opened = mutableListOf<String>()

    /** The real Navigator, over a stack that records every push. Screens reach it through LocalNavigator. */
    val navigator = Navigator(recording) { url -> opened.add(url) }

    private val registry = RouteRegistry().apply {
        registerSharedKeys()
        feature.registerEntries(this)
    }

    /** Every key pushed by navigation since the flow started, in order. The initial stack is not included. */
    val history: List<NavKey> get() = recording.recorded

    /** URLs that screens asked the navigator to open. Only URLs the navigator accepted are listed. */
    val openedUrls: List<String> get() = opened.toList()

    /** Sets the composition: the back stack is [keys], and the last key is the visible screen. */
    fun showStack(vararg keys: NavKey) {
        live.clear()
        live.addAll(keys)
        compose.setContent { FlowRoot() }
        compose.waitForIdle()
    }

    /** Shows a single screen, the usual case. */
    fun show(key: NavKey) = showStack(key)

    /** Reads a bean from the feature's real graph, for example a demo store or a repository. */
    inline fun <reified T : Any> get(): T = koin.get()

    /** Waits until a node whose text is exactly [text] exists. Fails with a Compose timeout after [timeoutMs]. */
    fun waitForText(text: String, timeoutMs: Long = FLOW_WAIT_TIMEOUT_MS) {
        compose.waitUntilAtLeastOneExists(hasText(text), timeoutMs)
    }

    /** Waits until no node with exactly [text] is left, for example after a dialog closes. */
    fun waitUntilTextGone(text: String, timeoutMs: Long = FLOW_WAIT_TIMEOUT_MS) {
        compose.waitUntilDoesNotExist(hasText(text), timeoutMs)
    }

    /** Waits until a node with [tag] exists. Use it when a screen has a test tag and the text is not unique. */
    fun waitForTag(tag: String, timeoutMs: Long = FLOW_WAIT_TIMEOUT_MS) {
        compose.waitUntilAtLeastOneExists(hasTestTag(tag), timeoutMs)
    }

    /**
     * Waits until [condition] holds, for state that is not on screen (a saved preference, a recorded call).
     *
     * Each poll first runs what is queued on the main looper. Work that hops from a background thread back to the main
     * thread (a DataStore edit started from a ViewModel does) only runs when the looper is idled, and Compose's own
     * `waitUntil` does not idle it, so without this the wait passes or times out depending on thread timing.
     */
    fun waitUntil(timeoutMs: Long = FLOW_WAIT_TIMEOUT_MS, condition: () -> Boolean) {
        compose.waitUntil(timeoutMs) {
            ShadowLooper.idleMainLooper()
            condition()
        }
    }

    /** Waits for the node with exactly [text] and returns its bounds in the window, in pixels. */
    fun boundsOf(text: String): Rect {
        waitForText(text)
        return compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot
    }

    /**
     * Waits for the node whose content description starts with [prefix] and returns its bounds. Charts announce their
     * title this way, so use it for them: their title is not a text node.
     */
    fun boundsOfDescription(prefix: String, timeoutMs: Long = FLOW_WAIT_TIMEOUT_MS): Rect {
        val matcher = hasContentDescription(prefix, substring = true)
        compose.waitUntilAtLeastOneExists(matcher, timeoutMs)
        return compose.onNode(matcher).fetchSemanticsNode().boundsInRoot
    }

    fun assertTextShown(text: String) {
        waitForText(text)
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    /**
     * Taps the clickable element that shows [text] (a list row, button or chip). Waits for it first. The click is sent to the
     * element itself; an element below the fold of a scrolling column is scrolled into view first.
     */
    fun clickText(text: String) {
        waitForText(text)
        val target = compose.onNode(hasText(text) and hasClickAction())
        if (isInsideScrollable(target.fetchSemanticsNode())) target.performScrollTo()
        // The click goes through the node's click action, not injected touch events, so it does not depend on touch timing.
        target.performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    /** Types [text] into the text field whose visible label is [label], replacing what it held. */
    fun typeIntoField(label: String, text: String) {
        val field = fieldLabelledBy(label)
        compose.onNode(fieldMatcher(field.id), useUnmergedTree = true).performTextReplacement(text)
        compose.waitForIdle()
    }

    /** Lets delayed work run. A screen that polls (builds, for example) schedules its next poll on the paused looper. */
    fun advanceClock(millis: Long) {
        ShadowLooper.idleMainLooper(millis, TimeUnit.MILLISECONDS)
        compose.waitForIdle()
    }

    fun assertNavigatedTo(key: NavKey) {
        if (key !in history) throw AssertionError("Expected a navigation to $key. Recorded: $history")
    }

    override fun close() {
        koinApp.close()
    }

    @Composable
    private fun FlowRoot() {
        CompositionLocalProvider(LocalNavigator provides navigator) {
            KoinIsolatedContext(koinApp) {
                VirgaTheme(darkTheme = darkTheme) {
                    NavDisplay(
                        backStack = live,
                        onBack = { navigator.back() },
                        entryDecorators = listOf(
                            rememberSaveableStateHolderNavEntryDecorator(),
                            rememberViewModelStoreNavEntryDecorator(),
                        ),
                        entryProvider = registry.buildEntryProvider(),
                    )
                }
            }
        }
    }

    /**
     * The editable node whose closest shared ancestor with the label is deepest. The label and its field sit in the same
     * small subtree, so a form with several fields still resolves to the right one.
     */
    private fun fieldLabelledBy(label: String): SemanticsNode {
        val labelNode = compose.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode()
        val labelChain = chainToRoot(labelNode)
        val labelIds = labelChain.map { it.id }.toSet()
        val scored = compose.onAllNodes(hasSetTextAction(), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .map { editable -> editable to sharedDepth(chainToRoot(editable), labelIds) }
            .filter { (_, depth) -> depth >= 0 }
        val deepest = scored.maxOfOrNull { it.second }
            ?: throw AssertionError("No text field is labelled \"$label\"")
        val closest = scored.filter { it.second == deepest }
        if (closest.size > 1) throw AssertionError("More than one text field is labelled \"$label\"")
        return closest.single().first
    }

    private fun isInsideScrollable(node: SemanticsNode): Boolean =
        chainToRoot(node).drop(1).any { ancestor ->
            ancestor.config.contains(SemanticsProperties.VerticalScrollAxisRange) ||
                ancestor.config.contains(SemanticsProperties.HorizontalScrollAxisRange)
        }

    private fun chainToRoot(node: SemanticsNode): List<SemanticsNode> = generateSequence(node) { it.parent }.toList()

    /** Depth from the root of the lowest ancestor of [chain] whose id is in [ids], or -1 when the trees do not meet. */
    private fun sharedDepth(chain: List<SemanticsNode>, ids: Set<Int>): Int {
        val index = chain.indexOfFirst { it.id in ids }
        return if (index < 0) -1 else chain.size - 1 - index
    }

    private fun fieldMatcher(id: Int): SemanticsMatcher = SemanticsMatcher("text field #$id") { it.id == id }

    private companion object {
        fun context(): Context = RuntimeEnvironment.getApplication()
    }
}
