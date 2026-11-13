package com.tomasps.virga.feature.home.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.tomasps.virga.core.common.builds.BuildSummary
import com.tomasps.virga.core.common.builds.BuildsGateway
import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.common.result.CloudflareMessage
import com.tomasps.virga.core.navigation.BuildDetail
import com.tomasps.virga.core.navigation.D1Database
import com.tomasps.virga.core.navigation.DomainsRoot
import com.tomasps.virga.core.navigation.KvNamespace
import com.tomasps.virga.core.navigation.PagesProject
import com.tomasps.virga.core.navigation.R2Bucket
import com.tomasps.virga.core.navigation.RouteRegistry
import com.tomasps.virga.core.navigation.WorkerDetail
import com.tomasps.virga.core.navigation.WorkersRoot
import com.tomasps.virga.core.navigation.ZoneDetail
import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.demo.DemoRouteContributor
import com.tomasps.virga.core.network.demo.DemoRouteRegistry
import com.tomasps.virga.feature.home.analytics.AnalyticsWindow
import com.tomasps.virga.feature.home.analytics.WorkersAnalytics
import com.tomasps.virga.feature.home.analytics.ZonesAnalytics
import com.tomasps.virga.feature.home.data.CloudflareHomeRepository
import com.tomasps.virga.feature.home.data.HomeDataRepository
import com.tomasps.virga.feature.home.demo.DEMO_SCRIPTS
import com.tomasps.virga.feature.home.demo.DEMO_ZONES
import com.tomasps.virga.feature.home.registerHomeEntries
import com.tomasps.virga.feature.home.search.SearchItem
import com.tomasps.virga.feature.home.search.SearchSource
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

/** Demo account ids the Home flows use. The personal account has every list; staging has none. */
internal const val PERSONAL_ACCOUNT = "demo-account-personal"
internal const val STAGING_ACCOUNT = "demo-account-staging"

/**
 * A Workers Builds gateway the test controls. [activeResponses] is answered in order; the last one repeats, so a test
 * can return a failure first and content on the retry. Every watch and stop call is recorded.
 */
class FlowBuildsGateway : BuildsGateway {
    val activeResponses: MutableList<ApiResult<List<BuildSummary>>> = mutableListOf(ApiResult.Success(emptyList()))

    /** When set, the active builds call waits for this before it answers, so a test can see the loading state. */
    var activeGate: CompletableDeferred<Unit>? = null
    val activeCalls = mutableListOf<String>()
    val started = mutableListOf<Pair<String, BuildSummary>>()
    val stopped = mutableListOf<String>()
    private val watched = MutableStateFlow<Set<String>>(emptySet())

    override suspend fun recentBuilds(accountId: String, scriptName: String, limit: Int): ApiResult<List<BuildSummary>> =
        ApiResult.Success(emptyList())

    override suspend fun activeBuilds(accountId: String): ApiResult<List<BuildSummary>> {
        activeGate?.await()
        activeCalls += accountId
        return if (activeResponses.size > 1) activeResponses.removeAt(0) else activeResponses.first()
    }

    override fun watchedBuildIds(): Flow<Set<String>> = watched.asStateFlow()

    override fun startWatching(accountId: String, build: BuildSummary) {
        started += accountId to build
        watched.update { it + build.buildUuid }
    }

    override fun stopWatching(buildUuid: String) {
        stopped += buildUuid
        watched.update { it - buildUuid }
    }
}

/** Which list endpoints answer 403 in a test, by the product name used in [FlowListRoutes]. */
class FlowLists {
    val forbidden: MutableSet<String> = mutableSetOf()

    /** Products whose list answers with a server error, so the source cannot be loaded at all. */
    val failing: MutableSet<String> = mutableSetOf()

    /** When true, the zone rows carry no status, as a domain list without one would. */
    var zonesWithoutStatus: Boolean = false
}

/**
 * Serves the six resource lists that Home searches, in the wire shapes its parser reads. The owning features answer
 * these in the app; Home's own flow test needs them so search and the shortcut lists have something to show.
 */
class FlowListRoutes(private val lists: FlowLists) : DemoRouteContributor {
    override fun contribute(registry: DemoRouteRegistry) {
        registry.get("/accounts/{accountId}/workers/scripts") { request ->
            guarded("workers", request.pathParams.getValue("accountId")) {
                DemoReply.ok(scriptsFor(request.pathParams.getValue("accountId")))
            }
        }
        registry.get("/zones") { request ->
            guarded("zones", request.query["account.id"].orEmpty()) {
                DemoReply.ok(zonesFor(request.query["account.id"].orEmpty()))
            }
        }
        registry.get("/accounts/{accountId}/pages/projects") { request ->
            guarded("pages", request.pathParams.getValue("accountId")) {
                DemoReply.ok(namedRows("name", listOf("sc-landing", "docs-preview")))
            }
        }
        registry.get("/accounts/{accountId}/r2/buckets") { request ->
            guarded("r2", request.pathParams.getValue("accountId")) {
                DemoReply.ok(buildJsonObject {
                    put("buckets", namedRows("name", listOf("assets-cdn")))
                })
            }
        }
        registry.get("/accounts/{accountId}/d1/database") { request ->
            guarded("d1", request.pathParams.getValue("accountId")) {
                DemoReply.ok(buildJsonArray {
                    add(buildJsonObject { put("uuid", "d1-ledger-uuid"); put("name", "ledger-db") })
                })
            }
        }
        registry.get("/accounts/{accountId}/storage/kv/namespaces") { request ->
            guarded("kv", request.pathParams.getValue("accountId")) {
                DemoReply.ok(buildJsonArray {
                    add(buildJsonObject { put("id", "kv-session-id"); put("title", "Session cache") })
                })
            }
        }
    }

    private inline fun guarded(product: String, accountId: String, answer: () -> DemoReply): DemoReply =
        if (product in lists.forbidden) {
            DemoReply.failure(HttpStatusCode.Forbidden, FORBIDDEN_CODE, "Not authorized")
        } else if (product in lists.failing) {
            DemoReply.failure(HttpStatusCode.InternalServerError, SERVER_ERROR_CODE, "Server error")
        } else if (accountId == STAGING_ACCOUNT) {
            DemoReply.ok(JsonArray(emptyList()))
        } else {
            answer()
        }

    /** The Workers the shipped demo serves, so Home's counts land on the same names. */
    private fun scriptsFor(@Suppress("UNUSED_PARAMETER") accountId: String): JsonElement =
        namedRows("id", DEMO_SCRIPTS.map { it.first })

    /** The zones the shipped Domains demo serves, with the same ids and statuses. */
    private fun zonesFor(@Suppress("UNUSED_PARAMETER") accountId: String): JsonElement = buildJsonArray {
        DEMO_ZONES.forEach { zone ->
            add(buildJsonObject {
                put("id", zone.id)
                put("name", zone.name)
                if (!lists.zonesWithoutStatus) put("status", zone.status)
            })
        }
    }

    private fun namedRows(key: String, values: List<String>): JsonArray = buildJsonArray {
        values.forEach { add(buildJsonObject { put(key, it) }) }
    }

    private companion object {
        const val FORBIDDEN_CODE = 10000
        const val SERVER_ERROR_CODE = 10001
    }
}

/** Route keys that Home opens. Each one gets a stub screen so a tap can be followed on screen. */
fun registerHomeFlowEntries(routes: RouteRegistry) {
    registerHomeEntries(routes)
    routes.route(WorkerDetail.serializer()) { StubScreen("stub: worker") }
    routes.route(BuildDetail.serializer()) { StubScreen("stub: build") }
    routes.route(ZoneDetail.serializer()) { StubScreen("stub: zone") }
    routes.route(PagesProject.serializer()) { StubScreen("stub: pages") }
    routes.route(R2Bucket.serializer()) { StubScreen("stub: r2") }
    routes.route(D1Database.serializer()) { StubScreen("stub: d1") }
    routes.route(KvNamespace.serializer()) { StubScreen("stub: kv") }
    routes.route(WorkersRoot.serializer()) { StubScreen("stub: workers list") }
    routes.route(DomainsRoot.serializer()) { StubScreen("stub: domains list") }
}

@Composable
private fun StubScreen(label: String) {
    Column { Text(label) }
}

/** Analytics answers a test can turn into errors. Null means the demo answer is used. */
class FlowAnalytics {
    var workersError: ApiError? = null
    var zonesError: ApiError? = null

    /** When set, the Worker analytics waits for this before it answers, so a test can see the loading state. */
    var workersGate: CompletableDeferred<Unit>? = null
}

/** The real Home repository, except that analytics can be made to fail. Lists always come from the real repository. */
private class FlowDataRepository(
    private val real: HomeDataRepository,
    private val analytics: FlowAnalytics,
) : HomeDataRepository {
    override suspend fun list(accountId: String, source: SearchSource, forceRefresh: Boolean): ApiResult<List<SearchItem>> =
        real.list(accountId, source, forceRefresh)

    override suspend fun workerAnalytics(accountId: String, window: AnalyticsWindow): ApiResult<WorkersAnalytics> {
        analytics.workersGate?.await()
        return analytics.workersError?.let { ApiResult.Failure(it) } ?: real.workerAnalytics(accountId, window)
    }

    override suspend fun zoneAnalytics(
        accountId: String,
        zoneIds: List<String>,
        window: AnalyticsWindow,
    ): ApiResult<ZonesAnalytics> = analytics.zonesError?.let { ApiResult.Failure(it) } ?: real.zoneAnalytics(accountId, zoneIds, window)
}

/**
 * Koin bindings for a Home flow: the fake gateway, the list routes, and the analytics override. The override is
 * declared after the feature module, so it replaces the real repository for this flow only.
 */
fun homeFlowModule(builds: FlowBuildsGateway, lists: FlowLists, analytics: FlowAnalytics): Module = module {
    single { builds } bind BuildsGateway::class
    single { FlowListRoutes(lists) } bind DemoRouteContributor::class
    single<HomeDataRepository> { FlowDataRepository(CloudflareHomeRepository(get()), analytics) }
}

/** An API error with the given message, for the analytics and permission states. */
fun graphQlError(message: String): ApiError = ApiError.GraphQl(listOf(CloudflareMessage(message = message)))
