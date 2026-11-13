package com.tomasps.virga.feature.builds.demo

import com.tomasps.virga.core.common.builds.BuildOutcome
import com.tomasps.virga.core.common.builds.BuildStatus
import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.demo.DemoRequest
import com.tomasps.virga.core.network.demo.DemoRouteContributor
import com.tomasps.virga.core.network.demo.DemoRouteRegistry
import com.tomasps.virga.core.network.demo.InMemoryStore
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.JsonNull

private const val LIVE_LOG_INTERVAL_MILLIS = 2_000L
private const val DEMO_TRIGGER_PREFIX = "demo-trigger-"
private const val DEMO_LIVE_UUID_PREFIX = "demo-live-"
private const val DEMO_PREVIEW_PREFIX = "demo-preview-"
private const val DEFAULT_PAGE_SIZE = 20
private const val FIRST_PAGE = 1
private const val NOT_FOUND_CODE = 10007
private const val NOT_FOUND_MESSAGE = "build not found"
private const val DEMO_BRANCH = "main"
private const val DEMO_COMMIT = "9f3c2ab"
private const val DEMO_COMMIT_MESSAGE = "Tune cache headers for assets"

/** A build the demo created from a trigger, or the seeded one. Its state is computed from [anchorMillis]. */
internal data class DemoLiveBuild(
    val scriptName: String,
    val anchorMillis: Long,
    val branch: String,
    val commitHash: String,
    val cancelledAtMillis: Long? = null,
)

/**
 * Canned Workers Builds routes for the demo account. Reads are computed from the clock and a small store; writes
 * (trigger, cancel, settings) change the store, so the next read shows them. The live build for [DEMO_LIVE_SCRIPT]
 * is seeded on first read, and every trigger starts a fresh one, so the demo can be repeated. Takes the clock as a
 * parameter so tests can move time.
 */
class BuildsDemoRoutes(private val clock: () -> Long = System::currentTimeMillis) : DemoRouteContributor {

    private val liveBuilds = InMemoryStore<DemoLiveBuild>()
    private val settings = InMemoryStore<JsonObject>()

    override fun contribute(registry: DemoRouteRegistry) {
        registry.get(BUILDS_OF_WORKER) { request -> listBuilds(request) }
        registry.get(BUILD) { request -> readBuild(request.pathParams["buildUuid"].orEmpty()) }
        registry.get(BUILD_LOGS) { request -> readLogs(request) }
        registry.put(BUILD_CANCEL) { request -> cancelBuild(request.pathParams["buildUuid"].orEmpty()) }
        registry.get(TRIGGERS_OF_WORKER) { request -> listTriggers(request.pathParams["workerTag"].orEmpty()) }
        registry.post(TRIGGER_BUILDS) { request -> startBuild(request) }
        registry.get(PREVIEWS_OF_WORKER) { request -> listPreviews(request.pathParams["workerTag"].orEmpty()) }
        registry.get(SETTINGS_OF_WORKER) { request -> readSettings(request.pathParams["workerTag"].orEmpty()) }
        registry.patch(SETTINGS_OF_WORKER) { request -> patchSettings(request) }
    }

    private fun listBuilds(request: DemoRequest): DemoReply {
        val name = scriptNameForDemoTag(request.pathParams["workerTag"].orEmpty())
            ?: return DemoReply.ok(JsonArray(emptyList()))
        val now = clock()
        ensureSeededLiveBuild(now)
        val live = liveBuilds.items.value
            .filterValues { it.scriptName == name }
            .map { (uuid, build) -> liveDemoBuild(uuid, build.anchorMillis, now, build.cancelledAtMillis, name).withTrigger(build) }
        val all = (live + historicalDemoBuilds(name, instantOf(now)).map { it }).sortedByDescending { it.createdOn }
        val page = request.query["page"]?.toIntOrNull()?.coerceAtLeast(FIRST_PAGE) ?: FIRST_PAGE
        val perPage = request.query["per_page"]?.toIntOrNull()?.coerceIn(1, MAX_PER_PAGE) ?: DEFAULT_PAGE_SIZE
        val slice = all.drop((page - FIRST_PAGE) * perPage).take(perPage)
        return DemoReply.ok(buildJsonArray { slice.forEach { add(it.toWireJson()) } })
    }

    private fun readBuild(uuid: String): DemoReply {
        val now = clock()
        ensureSeededLiveBuild(now)
        liveBuilds.get(uuid)?.let { build ->
            val live = liveDemoBuild(uuid, build.anchorMillis, now, build.cancelledAtMillis, build.scriptName).withTrigger(build)
            return DemoReply.ok(live.toWireJson())
        }
        historicalDemoBuilds(DEMO_LIVE_SCRIPT, instantOf(now)).firstOrNull { it.uuid == uuid }
            ?.let { return DemoReply.ok(it.toWireJson()) }
        return DemoReply.failure(HttpStatusCode.NotFound, NOT_FOUND_CODE, NOT_FOUND_MESSAGE)
    }

    private fun readLogs(request: DemoRequest): DemoReply {
        val uuid = request.pathParams["buildUuid"].orEmpty()
        val now = clock()
        ensureSeededLiveBuild(now)
        val live = liveBuilds.get(uuid)
        if (live != null) {
            val lines = liveDemoLogLines(live.anchorMillis, minOf(now, live.cancelledAtMillis ?: now))
            val from = request.query["cursor"]?.toIntOrNull()?.coerceIn(0, lines.size) ?: 0
            return DemoReply.ok(liveLogResult(lines, live.anchorMillis, from))
        }
        if (historicalDemoBuilds(DEMO_LIVE_SCRIPT, instantOf(now)).none { it.uuid == uuid }) {
            return DemoReply.failure(HttpStatusCode.NotFound, NOT_FOUND_CODE, NOT_FOUND_MESSAGE)
        }
        return DemoReply.ok(buildJsonObject { put("lines", logWire(FINISHED_LOG_LINES, 0L, 0, LIVE_LOG_INTERVAL_MILLIS)) })
    }

    private fun liveLogResult(lines: List<String>, anchorMillis: Long, from: Int): JsonObject = buildJsonObject {
        put("lines", logWire(lines, anchorMillis, from, LIVE_LOG_INTERVAL_MILLIS))
        put("cursor", lines.size.toString())
    }

    private fun cancelBuild(uuid: String): DemoReply {
        val build = liveBuilds.get(uuid) ?: return DemoReply.failure(HttpStatusCode.NotFound, NOT_FOUND_CODE, NOT_FOUND_MESSAGE)
        if (build.cancelledAtMillis == null) liveBuilds.put(uuid, build.copy(cancelledAtMillis = clock()))
        return DemoReply.ok(buildJsonObject { put("build_uuid", uuid) })
    }

    private fun listTriggers(workerTag: String): DemoReply {
        val name = scriptNameForDemoTag(workerTag) ?: return DemoReply.ok(JsonArray(emptyList()))
        return DemoReply.ok(buildJsonArray {
            add(buildJsonObject {
                put("trigger_uuid", DEMO_TRIGGER_PREFIX + name)
                put("trigger_name", "main")
                putJsonObject("repo_connection") { put("repo_name", name) }
                put("branch_includes", buildJsonArray { add(JsonPrimitive(DEMO_BRANCH)) })
            })
        })
    }

    private fun startBuild(request: DemoRequest): DemoReply {
        val triggerUuid = request.pathParams["triggerUuid"].orEmpty()
        val name = triggerUuid.removePrefix(DEMO_TRIGGER_PREFIX).takeIf { it.isNotEmpty() && it != triggerUuid }
            ?: return DemoReply.failure(HttpStatusCode.NotFound, NOT_FOUND_CODE, NOT_FOUND_MESSAGE)
        val body = request.bodyText?.let { parseBody(it) }
        val branch = body?.get("branch")?.let { (it as? JsonPrimitive)?.contentOrNull } ?: DEMO_BRANCH
        val commit = body?.get("commit_hash")?.let { (it as? JsonPrimitive)?.contentOrNull } ?: DEMO_COMMIT
        val now = clock()
        val uuid = DEMO_LIVE_UUID_PREFIX + now
        liveBuilds.put(uuid, DemoLiveBuild(name, anchorMillis = now, branch = branch, commitHash = commit))
        return DemoReply.ok(buildJsonObject {
            put("build_uuid", uuid)
            put("branch", branch)
            put("worker", name)
        })
    }

    private fun listPreviews(workerTag: String): DemoReply {
        val name = scriptNameForDemoTag(workerTag) ?: return DemoReply.ok(JsonArray(emptyList()))
        return DemoReply.ok(buildJsonArray {
            add(buildJsonObject {
                put("preview_id", DEMO_PREVIEW_PREFIX + name)
                put("branch", "feature/search")
                put("auto_build", true)
            })
        })
    }

    private fun readSettings(workerTag: String): DemoReply {
        val name = scriptNameForDemoTag(workerTag)
            ?: return DemoReply.failure(HttpStatusCode.NotFound, NOT_FOUND_CODE, NOT_FOUND_MESSAGE)
        return DemoReply.ok(settingsFor(workerTag, name))
    }

    private fun patchSettings(request: DemoRequest): DemoReply {
        val tag = request.pathParams["workerTag"].orEmpty()
        val name = scriptNameForDemoTag(tag)
            ?: return DemoReply.failure(HttpStatusCode.NotFound, NOT_FOUND_CODE, NOT_FOUND_MESSAGE)
        val patch = request.bodyText?.let { parseBody(it) } ?: JsonObject(emptyMap())
        val merged = settingsFor(tag, name).mergedWith(patch)
        settings.put(tag, merged)
        return DemoReply.ok(merged)
    }

    private fun settingsFor(tag: String, name: String): JsonObject = settings.get(tag) ?: defaultSettings(name)

    /** Makes sure the seeded live build exists. Its clock starts on the first read, so it is never stale. */
    private fun ensureSeededLiveBuild(now: Long) {
        if (liveBuilds.get(DEMO_LIVE_BUILD_UUID) == null) {
            liveBuilds.put(
                DEMO_LIVE_BUILD_UUID,
                DemoLiveBuild(DEMO_LIVE_SCRIPT, anchorMillis = now, branch = DEMO_BRANCH, commitHash = DEMO_COMMIT),
            )
        }
    }

    private companion object {
        const val MAX_PER_PAGE = 200
        const val BUILDS_OF_WORKER = "/accounts/{accountId}/builds/workers/{workerTag}/builds"
        const val BUILD = "/accounts/{accountId}/builds/builds/{buildUuid}"
        const val BUILD_LOGS = "/accounts/{accountId}/builds/builds/{buildUuid}/logs"
        const val BUILD_CANCEL = "/accounts/{accountId}/builds/builds/{buildUuid}/cancel"
        const val TRIGGERS_OF_WORKER = "/accounts/{accountId}/builds/workers/{workerTag}/triggers"
        const val TRIGGER_BUILDS = "/accounts/{accountId}/builds/triggers/{triggerUuid}/builds"
        const val PREVIEWS_OF_WORKER = "/accounts/{accountId}/builds/workers/{workerTag}/previews"
        const val SETTINGS_OF_WORKER = "/accounts/{accountId}/builds/workers/{workerTag}"
    }
}

/** The live build's wire form, with the branch and commit it was triggered for. */
private fun DemoBuild.withTrigger(build: DemoLiveBuild): DemoBuild =
    copy(branch = build.branch, commitHash = build.commitHash, commitMessage = DEMO_COMMIT_MESSAGE)

private fun parseBody(text: String): JsonObject? = runCatching {
    kotlinx.serialization.json.Json.parseToJsonElement(text).jsonObject
}.getOrNull()

/** Default demo settings of a connected Worker. The repository is a neutral invented one. */
internal fun defaultSettings(scriptName: String): JsonObject = buildJsonObject {
    putJsonObject("git_repository") {
        put("provider_type", "github")
        put("provider_account_name", "demo-owner")
        put("repo_name", scriptName)
        put("branch", DEMO_BRANCH)
    }
    putJsonObject("production_settings") {
        put("build_command", "npm run build")
        put("deploy_command", "npx wrangler deploy")
        put("root_directory", "/")
        put("build_caching_enabled", true)
    }
    put("previews_enabled", true)
}

/** Overlays a PATCH body on the stored settings, one level down, so a changed field keeps its siblings. */
internal fun JsonObject.mergedWith(patch: JsonObject): JsonObject = buildJsonObject {
    (keys + patch.keys).forEach { key ->
        val current: JsonElement? = this@mergedWith[key]
        val incoming: JsonElement? = patch[key]
        val value: JsonElement? = if (current is JsonObject && incoming is JsonObject) {
            current.mergedWith(incoming)
        } else {
            incoming ?: current
        }
        if (value != null && value !is JsonNull) put(key, value)
    }
}
