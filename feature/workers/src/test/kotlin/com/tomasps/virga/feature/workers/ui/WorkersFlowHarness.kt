package com.tomasps.virga.feature.workers.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.tomasps.virga.core.common.builds.BuildSummary
import com.tomasps.virga.core.common.builds.BuildsGateway
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.navigation.BuildDetail
import com.tomasps.virga.core.navigation.D1Database
import com.tomasps.virga.core.navigation.KvNamespace
import com.tomasps.virga.core.navigation.R2Bucket
import com.tomasps.virga.core.navigation.RouteRegistry
import com.tomasps.virga.core.navigation.WorkerBuilds
import com.tomasps.virga.core.testing.DemoFlow
import com.tomasps.virga.core.testing.FeatureUnderTest
import com.tomasps.virga.feature.workers.registerWorkersEntries
import com.tomasps.virga.feature.workers.workersKoinModule
import com.tomasps.virga.feature.workers.demo.WorkersDemoRoutes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.koin.dsl.module

internal const val DEMO_ACCOUNT_ID = "demo-account-personal"

/**
 * Builds gateway for flow tests. The real one lives in :feature:builds, which this flow does not start. Every call is
 * recorded so a test can assert what the screen asked for.
 */
internal class FlowBuildsGateway(latestByScript: Map<String, BuildSummary> = emptyMap(), watched: Set<String> = emptySet()) :
    BuildsGateway {
    private val latest = latestByScript.toMutableMap()
    private val watchedIds = MutableStateFlow(watched)
    val started = mutableListOf<String>()
    val stopped = mutableListOf<String>()

    override suspend fun recentBuilds(accountId: String, scriptName: String, limit: Int): ApiResult<List<BuildSummary>> =
        ApiResult.Success(listOfNotNull(latest[scriptName]).take(limit))

    override suspend fun activeBuilds(accountId: String): ApiResult<List<BuildSummary>> =
        ApiResult.Success(latest.values.filter { it.status.isActive })

    override fun watchedBuildIds(): Flow<Set<String>> = watchedIds

    override fun startWatching(accountId: String, build: BuildSummary) {
        started += build.buildUuid
        watchedIds.update { it + build.buildUuid }
    }

    override fun stopWatching(buildUuid: String) {
        stopped += buildUuid
        watchedIds.update { it - buildUuid }
    }
}

/**
 * A flow for the Workers feature with stub screens for the features it links to (R2, KV, D1, builds), so a tap on a
 * binding or a build can be asserted through the recorded navigation.
 */
internal fun workersFlow(compose: ComposeContentTestRule, builds: FlowBuildsGateway = FlowBuildsGateway()): DemoFlow =
    DemoFlow(
        compose = compose,
        feature = FeatureUnderTest(
            koinModules = listOf(workersKoinModule, module { single<BuildsGateway> { builds } }),
            registerEntries = ::registerWorkersWithLinkedStubs,
        ),
    )

/** The demo state behind the flow, so a test can read what a write changed. */
internal fun DemoFlow.demoRoutes(): WorkersDemoRoutes = get()

private fun registerWorkersWithLinkedStubs(routes: RouteRegistry) {
    registerWorkersEntries(routes)
    routes.route(R2Bucket.serializer()) { Text("Stub R2 bucket") }
    routes.route(KvNamespace.serializer()) { Text("Stub KV namespace") }
    routes.route(D1Database.serializer()) { Text("Stub D1 database") }
    routes.route(WorkerBuilds.serializer()) { Text("Stub builds") }
    routes.route(BuildDetail.serializer()) { Text("Stub build detail") }
}

/** The demo state that a write changed, read back the way the next screen read sees it. */
private fun DemoFlow.demoState() = get<WorkersDemoRoutes>().state

internal fun DemoFlow.subdomainEnabled(script: String): Boolean =
    demoState().subdomains.get(script)?.get("enabled")?.jsonPrimitive?.booleanOrNull == true

internal fun DemoFlow.previewsEnabled(script: String): Boolean =
    demoState().subdomains.get(script)?.get("previews_enabled")?.jsonPrimitive?.booleanOrNull == true

internal fun DemoFlow.hasDomain(script: String, hostname: String): Boolean =
    demoState().domains.get(script).orEmpty().any { it.jsonObject["hostname"]?.jsonPrimitive?.content == hostname }

internal fun DemoFlow.hasSecret(script: String, name: String): Boolean =
    demoState().secretNames.get(script).orEmpty().any { it.jsonPrimitive.content == name }

internal fun DemoFlow.hasScript(name: String): Boolean = demoState().scripts.get(name) != null

internal fun DemoFlow.deploymentCount(script: String): Int = demoState().deployments.get(script)?.size ?: 0

/** The newest deployment's split as (version number, percent), so a test asserts what the user would see. */
internal fun DemoFlow.activeSplit(script: String): List<Pair<Int, Double>> {
    val state = demoState()
    val numbers = state.versions.get(script).orEmpty().associate { version ->
        val obj = version.jsonObject
        obj.getValue("id").jsonPrimitive.content to obj.getValue("number").jsonPrimitive.int
    }
    val split = state.deployments.get(script)?.firstOrNull()?.jsonObject?.get("versions")?.jsonArray.orEmpty()
    return split.map { element ->
        val obj = element.jsonObject
        numbers.getValue(obj.getValue("version_id").jsonPrimitive.content) to
            obj.getValue("percentage").jsonPrimitive.double
    }
}
