package com.tomasps.virga.feature.kv.ui

import androidx.compose.ui.test.junit4.createComposeRule
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.navigation.KvNamespace
import com.tomasps.virga.core.navigation.KvRoot
import com.tomasps.virga.core.testing.DemoFlow
import com.tomasps.virga.core.testing.FeatureUnderTest
import com.tomasps.virga.feature.kv.KvAddKey
import com.tomasps.virga.feature.kv.KvKeyDetail
import com.tomasps.virga.feature.kv.data.KvRepository
import com.tomasps.virga.feature.kv.demo.KV_DEMO_NS_FLAGS
import com.tomasps.virga.feature.kv.demo.KvDemoStore
import com.tomasps.virga.feature.kv.kvKoinModule
import com.tomasps.virga.feature.kv.registerKvEntries
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val DEMO_PERSONAL_ACCOUNT = "demo-account-personal"
private const val INVALID_METADATA_MESSAGE = "Metadata must be a JSON value. Check the brackets and quotes."

/** Screen-level flows for KV in demo mode, through the feature's real Koin graph and real route registration. */
@RunWith(RobolectricTestRunner::class)
class KvDemoFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var flow: DemoFlow

    @Before
    fun setUp() {
        flow = DemoFlow(compose, FeatureUnderTest(listOf(kvKoinModule), ::registerKvEntries))
    }

    @After
    fun tearDown() {
        flow.close()
    }

    @Test
    fun `namespaces list shows the demo namespaces`() {
        flow.show(KvRoot)

        flow.assertTextShown("Session cache")
        flow.assertTextShown("Feature flags")
        flow.assertTextShown("Asset manifest")
    }

    @Test
    fun `opening a namespace lists its keys, and tapping one opens the key detail`() {
        flow.show(KvRoot)

        flow.clickText("Feature flags")
        flow.assertTextShown("checkout/new-flow")
        flow.clickText("checkout/new-flow")

        flow.assertNavigatedTo(KvKeyDetail(KV_DEMO_NS_FLAGS, "checkout/new-flow"))
        flow.waitForText("Show value")
    }

    @Test
    fun `add key with invalid metadata shows the validation message and writes nothing`() {
        flow.showStack(KvNamespace(KV_DEMO_NS_FLAGS), KvAddKey(KV_DEMO_NS_FLAGS))

        flow.typeIntoField("Key name", "flow/invalid-metadata")
        flow.typeIntoField("Value", "hello")
        flow.typeIntoField("Metadata (JSON)", "{not json")
        flow.clickText("Save key")

        flow.waitForText(INVALID_METADATA_MESSAGE)
        flow.assertTextShown("Add key")
        val store = flow.get<KvDemoStore>()
        assertNull(store.entries.get(store.entryKey(DEMO_PERSONAL_ACCOUNT, KV_DEMO_NS_FLAGS, "flow/invalid-metadata")))
    }

    @Test
    fun `add key with valid input returns to the namespace and lists the new key`() {
        flow.showStack(KvNamespace(KV_DEMO_NS_FLAGS), KvAddKey(KV_DEMO_NS_FLAGS))

        flow.typeIntoField("Key name", "flow/new-key")
        flow.typeIntoField("Value", "hello from the flow test")
        flow.clickText("Save key")

        flow.waitUntilTextGone("Save key")
        flow.waitForText("Feature flags")
        flow.assertTextShown("flow/new-key")
        val lookup = runBlocking {
            flow.get<KvRepository>().findKey(DEMO_PERSONAL_ACCOUNT, KV_DEMO_NS_FLAGS, "flow/new-key")
        }
        assertNotNull((lookup as ApiResult.Success).value)
    }
}
