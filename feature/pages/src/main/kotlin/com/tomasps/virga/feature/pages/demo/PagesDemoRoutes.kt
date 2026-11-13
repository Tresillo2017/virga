package com.tomasps.virga.feature.pages.demo

import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.demo.DemoRequest
import com.tomasps.virga.core.network.demo.DemoRouteContributor
import com.tomasps.virga.core.network.demo.DemoRouteRegistry
import com.tomasps.virga.core.network.demo.InMemoryStore
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import com.tomasps.virga.feature.pages.logic.PagesStageName
import com.tomasps.virga.feature.pages.logic.PagesStatus
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val NOT_FOUND_CODE = 8000007
private const val BAD_REQUEST_CODE = 8000024
private const val DEMO_NOW = "2026-11-11T12:00:00Z"
private const val DEMO_TOKEN_SUFFIX = "-token"

/**
 * Canned Pages responses for the demo account. Writes change the in-memory stores, so the next read shows them.
 * Deployments in progress advance one stage each time they are read, so the progress view moves in the demo.
 */
class PagesDemoRoutes : DemoRouteContributor {

    private val projects = InMemoryStore(seedProjects())
    private val deployments = InMemoryStore(seedDeployments())
    private val domains = InMemoryStore(seedDomains())

    override fun contribute(registry: DemoRouteRegistry) {
        registry.get(PROJECTS) { DemoReply.ok(JsonArray(projects.items.value.values.map { public(withDeployments(it)) })) }
        registry.get(PROJECT) { request -> withProject(request) { DemoReply.ok(public(withDeployments(it))) } }
        registry.patch(PROJECT) { request -> patchProject(request) }
        registry.delete(PROJECT) { request -> withProject(request) { deleteProject(it) } }
        registry.post(PURGE) { request -> withProject(request) { DemoReply.ok(JsonNull) } }
        registry.get(DEPLOYMENTS) { request -> withProject(request) { listDeployments(it, request) } }
        registry.get(DEPLOYMENT) { request -> withDeployment(request) { advance(it) } }
        registry.delete(DEPLOYMENT) { request -> withDeployment(request) { removeDeployment(it) } }
        registry.post(RETRY) { request -> withDeployment(request) { retry(it) } }
        registry.post(ROLLBACK) { request -> withDeployment(request) { rollback(it) } }
        registry.get(LOGS) { request -> withDeployment(request) { DemoReply.ok(logsFor(it)) } }
        registry.get(DOMAINS) { request -> withProject(request) { listDomains(it) } }
        registry.post(DOMAINS) { request -> withProject(request) { addDomain(it, request) } }
        registry.patch(DOMAIN) { request -> withDomain(request) { retryValidation(it) } }
        registry.delete(DOMAIN) { request -> withDomain(request) { removeDomain(it) } }
    }

    private fun withProject(request: DemoRequest, reply: (JsonObject) -> DemoReply): DemoReply {
        val name = request.pathParams["projectName"].orEmpty()
        val project = projects.get(name) ?: return notFound()
        return reply(project)
    }

    private fun withDeployment(request: DemoRequest, reply: (JsonObject) -> DemoReply): DemoReply {
        val id = request.pathParams["deploymentId"].orEmpty()
        val deployment = deployments.get(id) ?: return notFound()
        return reply(deployment)
    }

    private fun withDomain(request: DemoRequest, reply: (JsonObject) -> DemoReply): DemoReply {
        val key = "${request.pathParams["projectName"].orEmpty()}/${request.pathParams["domainName"].orEmpty()}"
        val domain = domains.get(key) ?: return notFound()
        return reply(domain)
    }

    private fun patchProject(request: DemoRequest): DemoReply = withProject(request) { project ->
        val patch = parseObject(request.bodyText.orEmpty()) ?: return@withProject badRequest()
        val merged = deepMerge(project, patch)
        projects.put(project.string("name"), merged)
        DemoReply.ok(public(merged))
    }

    private fun deleteProject(project: JsonObject): DemoReply {
        projects.remove(project.string("name"))
        return DemoReply.ok(JsonNull)
    }

    private fun listDeployments(project: JsonObject, request: DemoRequest): DemoReply {
        val name = project.string("name")
        val environment = request.query["env"]
        val all = deployments.items.value.values
            .filter { it.string("project_name") == name }
            .filter { environment == null || it.string("environment") == environment }
            .sortedByDescending { it.string("created_on") }
        val perPage = request.query["per_page"]?.toIntOrNull() ?: DEFAULT_PER_PAGE
        val page = request.query["page"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val slice = all.drop((page - 1) * perPage).take(perPage)
        return DemoReply.ok(
            JsonArray(slice.map { public(it) }),
            buildJsonObject {
                put("page", page)
                put("per_page", perPage)
                put("count", slice.size)
                put("total_count", all.size)
            },
        )
    }

    /** Each read of an in-progress deployment moves it one stage on. */
    private fun advance(deployment: JsonObject): DemoReply {
        val stages = deployment["stages"] as? JsonArray ?: return DemoReply.ok(public(deployment))
        val updated = deployment.withStages(nextStageStates(stages))
        deployments.put(deployment.string("id"), updated)
        return DemoReply.ok(public(updated))
    }

    private fun removeDeployment(deployment: JsonObject): DemoReply {
        if (deployment.string("id") == CURRENT_PRODUCTION) {
            return DemoReply.failure(HttpStatusCode.BadRequest, BAD_REQUEST_CODE, "The production deployment cannot be deleted")
        }
        deployments.remove(deployment.string("id"))
        return DemoReply.ok(JsonNull)
    }

    private fun retry(deployment: JsonObject): DemoReply {
        val queued = pipelineStages { name -> if (name == PagesStageName.QUEUED) PagesStatus.ACTIVE else PagesStatus.IDLE }
        val updated = deployment.withStages(queued)
        deployments.put(deployment.string("id"), updated)
        return DemoReply.ok(public(updated))
    }

    /** Makes the target the newest production deployment; the project then reports it as current. */
    private fun rollback(deployment: JsonObject): DemoReply {
        val updated = JsonObject(deployment + ("created_on" to JsonPrimitive(DEMO_NOW)))
        deployments.put(deployment.string("id"), updated)
        return DemoReply.ok(public(updated))
    }

    private fun logsFor(deployment: JsonObject): JsonObject {
        val lines = if (deployment.latestStatus() == PagesStatus.FAILURE) FAILED_LOG else SUCCESS_LOG
        return buildJsonObject {
            put("total", lines.size)
            put("includes_container_logs", false)
            put("data", buildJsonArray {
                lines.forEach { line -> add(buildJsonObject { put("ts", "2026-11-11T09:20:00Z"); put("line", line) }) }
            })
        }
    }

    private fun listDomains(project: JsonObject): DemoReply {
        val name = project.string("name")
        val list = domains.items.value.values.filter { it.string("_project") == name }
        return DemoReply.ok(JsonArray(list.map { public(it) }))
    }

    private fun addDomain(project: JsonObject, request: DemoRequest): DemoReply {
        val hostname = parseObject(request.bodyText.orEmpty())?.string("name")?.trim()?.lowercase().orEmpty()
        if (hostname.isBlank()) return badRequest()
        val key = "${project.string("name")}/$hostname"
        val domain = domain(project.string("name"), hostname, "pending")
        domains.put(key, domain)
        return DemoReply.ok(public(domain))
    }

    private fun retryValidation(domain: JsonObject): DemoReply {
        val active = domain(domain.string("_project"), domain.string("name"), "active")
        domains.put("${domain.string("_project")}/${domain.string("name")}", active)
        return DemoReply.ok(public(active))
    }

    private fun removeDomain(domain: JsonObject): DemoReply {
        domains.remove("${domain.string("_project")}/${domain.string("name")}")
        return DemoReply.ok(JsonNull)
    }

    /** Real project reads carry the newest deployment and the newest production one; the list status comes from the former. */
    private fun withDeployments(project: JsonObject): JsonObject {
        val name = project.string("name")
        val mine = deployments.items.value.values.filter { it.string("project_name") == name }
        val latest = mine.maxByOrNull { it.string("created_on") }
        val canonical = mine.filter { it.string("environment") == "production" }.maxByOrNull { it.string("created_on") }
        return JsonObject(
            project + listOfNotNull(
                latest?.let { "latest_deployment" to it },
                canonical?.let { "canonical_deployment" to it },
            ),
        )
    }

    private fun public(json: JsonObject): JsonObject = JsonObject(json - "_project")

    private fun notFound(): DemoReply = DemoReply.failure(HttpStatusCode.NotFound, NOT_FOUND_CODE, "Not found")

    private fun badRequest(): DemoReply = DemoReply.failure(HttpStatusCode.BadRequest, BAD_REQUEST_CODE, "Invalid request")

    private companion object {
        const val DEFAULT_PER_PAGE = 25
        const val BASE = "/accounts/{accountId}/pages/projects"
        const val PROJECTS = BASE
        const val PROJECT = "$BASE/{projectName}"
        const val PURGE = "$PROJECT/purge_build_cache"
        const val DEPLOYMENTS = "$PROJECT/deployments"
        const val DEPLOYMENT = "$DEPLOYMENTS/{deploymentId}"
        const val RETRY = "$DEPLOYMENT/retry"
        const val ROLLBACK = "$DEPLOYMENT/rollback"
        const val LOGS = "$DEPLOYMENT/history/logs"
        const val DOMAINS = "$PROJECT/domains"
        const val DOMAIN = "$DOMAINS/{domainName}"
        val SUCCESS_LOG = listOf("Cloning repository...", "Installing dependencies", "Build complete", "Uploading 84 files", "Deployment complete")
        val FAILED_LOG = listOf("Cloning repository...", "Installing dependencies", "Build failed with exit code 1")
    }
}
