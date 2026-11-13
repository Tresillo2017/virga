package com.tomasps.virga.feature.kv.ui

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.navigation3.runtime.NavKey
import androidx.test.espresso.AppNotIdleException
import androidx.test.espresso.IdlingPolicies
import com.tomasps.virga.core.testing.DemoFlow
import com.tomasps.virga.core.testing.FeatureUnderTest
import com.tomasps.virga.feature.kv.demo.KV_DEMO_ACCOUNT_PERSONAL
import com.tomasps.virga.feature.kv.demo.KV_DEMO_NS_FLAGS
import com.tomasps.virga.feature.kv.demo.KvDemoStore
import com.tomasps.virga.feature.kv.kvKoinModule
import com.tomasps.virga.feature.kv.registerKvEntries
import com.tomasps.virga.feature.kv.value.KvWriteThrottle
import org.koin.dsl.module
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

private const val IDLE_TIMEOUT_SECONDS = 5L
private const val DEFAULT_IDLE_TIMEOUT_SECONDS = 60L
private const val LOAD_SETTLE_MILLIS = 2_000L

/** A clock the tests move by hand. Used by the per-key write limit, which otherwise reads the real clock. */
class FakeMillisClock(var nowMillis: Long = 1_700_000_000_000L)

/** KV's real Koin graph, with the write limit reading [clock] instead of the wall clock. */
fun kvFeatureUnderTest(clock: FakeMillisClock = FakeMillisClock()) = FeatureUnderTest(
    koinModules = listOf(
        kvKoinModule,
        module { single { KvWriteThrottle { clock.nowMillis } } },
    ),
    registerEntries = ::registerKvEntries,
)

fun kvDemoFlow(compose: ComposeContentTestRule, clock: FakeMillisClock = FakeMillisClock()): DemoFlow =
    DemoFlow(compose, kvFeatureUnderTest(clock))

/**
 * Shortens Espresso's idle timeout for this test. Demo answers come back on a real thread, and the paused looper only
 * delivers them when the clock moves. A screen that is still loading then never reads as idle, so its first wait
 * would otherwise run for a full minute. Call from @Before, and [restoreIdleTimeout] from @After.
 */
fun quickIdleTimeout() {
    IdlingPolicies.setMasterPolicyTimeout(IDLE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
}

fun restoreIdleTimeout() {
    IdlingPolicies.setMasterPolicyTimeout(DEFAULT_IDLE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
}

/**
 * Shows [keys] like [DemoFlow.showStack], then lets a screen's first load finish. The composition is set before the
 * idle wait fails, so the screen is there either way; advancing the paused clock delivers the demo answer.
 */
fun DemoFlow.showSettled(vararg keys: NavKey) {
    try {
        showStack(*keys)
    } catch (busy: AppNotIdleException) {
        ShadowLooper.idleMainLooper(LOAD_SETTLE_MILLIS, TimeUnit.MILLISECONDS)
    }
}

/** The stored entry for [keyName] in the demo flags namespace of the personal account, or null. */
internal fun DemoFlow.storedEntry(keyName: String, accountId: String = KV_DEMO_ACCOUNT_PERSONAL, namespaceId: String = KV_DEMO_NS_FLAGS) =
    get<KvDemoStore>().let { store -> store.entries.get(store.entryKey(accountId, namespaceId, keyName)) }

/** Why a dialog test is ignored. A dialog with a text field never reads as idle under Robolectric; see the report. */
const val DIALOG_IDLE_LIMIT =
    "Compose never reads as idle while an alert dialog with a text field is open (AppNotIdleException under Robolectric). " +
    "The dialog's view model is covered by unit tests."
