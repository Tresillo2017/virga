package com.tomasps.virga.feature.kv.ui

import androidx.compose.ui.test.junit4.createComposeRule
import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.navigation.KvNamespace
import com.tomasps.virga.core.testing.DemoFlow
import com.tomasps.virga.core.testing.FeatureUnderTest
import com.tomasps.virga.feature.kv.data.KvMetrics
import com.tomasps.virga.feature.kv.data.KvMetricsSource
import com.tomasps.virga.feature.kv.demo.KV_DEMO_NS_FLAGS
import com.tomasps.virga.feature.kv.kvKoinModule
import com.tomasps.virga.feature.kv.registerKvEntries
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/** The metrics tab with the analytics source replaced: no data, a failure, and the retry after it. */
@RunWith(RobolectricTestRunner::class)
class KvMetricsFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private var flow: DemoFlow? = null

    @Before
    fun setUp() {
        quickIdleTimeout()
    }

    @After
    fun tearDown() {
        flow?.close()
        restoreIdleTimeout()
    }

    private fun flowWith(source: KvMetricsSource): DemoFlow {
        val feature = FeatureUnderTest(
            koinModules = listOf(kvKoinModule, module { single<KvMetricsSource> { source } }),
            registerEntries = ::registerKvEntries,
        )
        return DemoFlow(compose, feature).also { flow = it }
    }

    @Test
    fun `a namespace with no analytics shows the metrics empty state`() {
        val empty = object : KvMetricsSource {
            override suspend fun load(accountId: String, namespaceId: String, start: LocalDate, end: LocalDate) =
                ApiResult.Success(KvMetrics(operations = emptyList(), storage = emptyList()))
        }
        val demo = flowWith(empty)
        demo.showSettled(KvNamespace(KV_DEMO_NS_FLAGS))

        demo.clickText("Metrics")

        demo.assertTextShown("No analytics yet")
        demo.assertTextShown("KV analytics appear here after the namespace has been used.")
    }

    @Test
    fun `a failed analytics load shows the error with a retry that asks the source again`() {
        var calls = 0
        val failing = object : KvMetricsSource {
            override suspend fun load(accountId: String, namespaceId: String, start: LocalDate, end: LocalDate): ApiResult<KvMetrics> {
                calls++
                return ApiResult.Failure(ApiError.Local(reason = "metrics unavailable"))
            }
        }
        val demo = flowWith(failing)
        demo.showSettled(KvNamespace(KV_DEMO_NS_FLAGS))
        demo.clickText("Metrics")
        demo.assertTextShown("Retry")
        val before = calls

        demo.clickText("Retry")

        demo.waitUntil { calls > before }
        assertEquals(before + 1, calls)
    }
}
