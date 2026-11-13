package com.tomasps.virga.feature.workers.demo

import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.demo.DemoRequest
import com.tomasps.virga.core.network.demo.DemoRouteContributor
import com.tomasps.virga.core.network.demo.DemoRouteRegistry
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private const val SCRIPT_NOT_FOUND_CODE = 10007
private const val SCRIPT_NOT_FOUND_MESSAGE = "Worker not found."
private const val DEMO_NOW = "2026-11-11T09:00:00Z"

/**
 * Canned Worker routes for the demo account. Reads come from [WorkersDemoState]; writes update it, so the screens
 * change after an action. The GraphQL metrics reads are not covered here: POST /graphql is shared by every feature,
 * so it needs a core dispatch hook first.
 */
class WorkersDemoRoutes internal constructor(
    internal val state: WorkersDemoState,
) : DemoRouteContributor {

    constructor() : this(WorkersDemoState())

    override fun contribute(registry: DemoRouteRegistry) {
        registry.get(SCRIPTS) { DemoReply.ok(scriptList()) }
        registry.graphQl(WORKER_INVOCATIONS_OPERATION) { request -> workerInvocationsReply(request) }
        registry.get(SCRIPT) { request -> withScript(request) { name -> DemoReply.ok(state.scripts.get(name) ?: JsonObject(emptyMap())) } }
        registry.delete(SCRIPT) { request ->
            withScript(request) { name ->
                state.scripts.remove(name)
                DemoReply.empty()
            }
        }
        registry.get(SETTINGS) { request -> withScript(request) { name -> DemoReply.ok(settingsJson(name)) } }
        registry.get(DEPLOYMENTS) { request ->
            withScript(request) { name -> DemoReply.ok(buildJsonObject { put("deployments", deploymentsFor(name)) }) }
        }
        registry.post(DEPLOYMENTS) { request -> withScript(request) { name -> createDeployment(name, request) } }
        registry.get(VERSIONS) { request ->
            withScript(request) { name -> DemoReply.ok(buildJsonObject { put("items", versionsFor(name)) }) }
        }
        registry.get(SUBDOMAIN) { request ->
            withScript(request) { name -> DemoReply.ok(state.subdomains.get(name) ?: subdomainJson(false, false)) }
        }
        registry.post(SUBDOMAIN) { request -> withScript(request) { name -> saveSubdomain(name, request) } }
        registry.patch(SECRETS_BULK) { request -> withScript(request) { name -> saveSecrets(name, request) } }
        registry.get(DOMAINS) { request ->
            val service = request.query["service"]
            val all = state.domains.items.value.values.flatMap { it.toList() }
            DemoReply.ok(buildJsonArray { all.filter { service == null || it.jsonObject["service"]?.jsonPrimitive?.content == service }.forEach { add(it) } })
        }
        registry.put(DOMAINS) { request -> addDomain(request) }
        registry.delete(DOMAIN) { request ->
            val domainId = request.pathParams["domainId"].orEmpty()
            val owner = state.domains.items.value.entries.firstOrNull { (_, list) -> list.any { it.jsonObject["id"]?.jsonPrimitive?.content == domainId } }
            if (owner == null) {
                DemoReply.failure(HttpStatusCode.NotFound, code = 10000, message = "Domain not found.")
            } else {
                state.domains.put(owner.key, buildJsonArray { owner.value.filterNot { it.jsonObject["id"]?.jsonPrimitive?.content == domainId }.forEach { add(it) } })
                DemoReply.empty()
            }
        }
    }

    private fun scriptList(): JsonArray = buildJsonArray {
        state.scripts.items.value.values.forEach { add(it) }
    }

    private fun withScript(request: DemoRequest, block: (String) -> DemoReply): DemoReply {
        val name = request.pathParams["scriptName"].orEmpty()
        return if (state.scripts.get(name) == null) {
            DemoReply.failure(HttpStatusCode.NotFound, SCRIPT_NOT_FOUND_CODE, SCRIPT_NOT_FOUND_MESSAGE)
        } else {
            block(name)
        }
    }

    private fun settingsJson(name: String): JsonObject = buildJsonObject {
        put("compatibility_date", "2026-10-01")
        put("compatibility_flags", buildJsonArray { add(JsonPrimitive("nodejs_compat")) })
        put("logpush", false)
        put("bindings", bindingsFor(name))
    }

    private fun bindingsFor(name: String): JsonArray = buildJsonArray {
        when (name) {
            DEMO_SCRIPT_EDGE_API -> {
                add(buildJsonObject { put("type", "kv_namespace"); put("name", "CACHE"); put("namespace_id", DEMO_KV_NAMESPACE_ID) })
                add(buildJsonObject { put("type", "r2_bucket"); put("name", "ASSETS"); put("bucket_name", DEMO_R2_BUCKET_NAME) })
                add(buildJsonObject { put("type", "d1"); put("name", "ORDERS"); put("database_id", DEMO_D1_DATABASE_ID) })
                add(buildJsonObject { put("type", "plain_text"); put("name", "LOG_LEVEL"); put("text", "info") })
            }
            DEMO_SCRIPT_RESIZER -> {
                add(buildJsonObject { put("type", "r2_bucket"); put("name", "SOURCE"); put("bucket_name", DEMO_R2_BUCKET_NAME) })
                add(buildJsonObject { put("type", "plain_text"); put("name", "MAX_PIXELS"); put("text", "4000000") })
            }
            else -> add(buildJsonObject { put("type", "d1"); put("name", "ORDERS"); put("database_id", DEMO_D1_DATABASE_ID) })
        }
        state.secretNames.get(name).orEmpty().forEach { secret ->
            add(buildJsonObject {
                put("type", "secret_text")
                put("name", secret.jsonPrimitive.content)
            })
        }
    }

    private fun versionsFor(name: String): JsonArray = state.versions.get(name) ?: JsonArray(emptyList())

    private fun deploymentsFor(name: String): JsonArray = state.deployments.get(name) ?: JsonArray(emptyList())

    private fun createDeployment(name: String, request: DemoRequest): DemoReply {
        val body = request.bodyText.orEmpty().takeIf { it.isNotBlank() } ?: return invalidBody()
        val versions = runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(body).jsonObject["versions"]?.jsonArray
        }.getOrNull() ?: return invalidBody()
        val split = versions.mapNotNull { element: JsonElement ->
            val obj = element.jsonObject
            val versionId = obj["version_id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            versionId to (obj["percentage"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0)
        }
        if (split.isEmpty()) return invalidBody()
        val deployment = deploymentJson("dep-${name.take(8)}-${split.hashCode().toUInt()}", DEMO_NOW, split, null)
        state.deployments.put(name, buildJsonArray { add(deployment); deploymentsFor(name).forEach { add(it) } })
        return DemoReply.ok(deployment)
    }

    private fun invalidBody(): DemoReply = DemoReply.failure(HttpStatusCode.BadRequest, code = 10001, message = "Invalid request body.")

    private fun saveSubdomain(name: String, request: DemoRequest): DemoReply {
        val obj = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(request.bodyText.orEmpty()).jsonObject }.getOrNull()
            ?: return invalidBody()
        val enabled = obj["enabled"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
        val previews = obj["previews_enabled"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
        val saved = subdomainJson(enabled, previews)
        state.subdomains.put(name, saved)
        return DemoReply.ok(saved)
    }

    private fun addDomain(request: DemoRequest): DemoReply {
        val obj = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(request.bodyText.orEmpty()).jsonObject }.getOrNull()
            ?: return invalidBody()
        val hostname = obj["hostname"]?.jsonPrimitive?.content.orEmpty()
        val service = obj["service"]?.jsonPrimitive?.content.orEmpty()
        if (hostname.isBlank() || service.isBlank()) return invalidBody()
        val domain = buildJsonObject {
            put("id", "demo-domain-" + hostname.replace('.', '-'))
            put("hostname", hostname)
            put("service", service)
            put("zone_name", hostname.split('.').takeLast(2).joinToString("."))
            put("enabled", true)
            put("previews_enabled", false)
        }
        state.domains.put(service, buildJsonArray {
            state.domains.get(service).orEmpty().forEach { add(it) }
            add(domain)
        })
        return DemoReply.ok(domain)
    }

    private fun saveSecrets(name: String, request: DemoRequest): DemoReply {
        val secrets = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(request.bodyText.orEmpty()).jsonObject["secrets"]?.jsonObject }
            .getOrNull() ?: return invalidBody()
        val current = state.secretNames.get(name).orEmpty().map { it.jsonPrimitive.content }.toMutableSet()
        secrets.forEach { (secretName, value) ->
            if (value is JsonObject) current += secretName else current -= secretName
        }
        state.secretNames.put(name, buildJsonArray { current.sorted().forEach { add(JsonPrimitive(it)) } })
        return DemoReply.ok(buildJsonObject { })
    }

    private companion object {
        const val SCRIPTS = "/accounts/{accountId}/workers/scripts"
        const val SCRIPT = "/accounts/{accountId}/workers/scripts/{scriptName}"
        const val SETTINGS = "/accounts/{accountId}/workers/scripts/{scriptName}/settings"
        const val DEPLOYMENTS = "/accounts/{accountId}/workers/scripts/{scriptName}/deployments"
        const val SUBDOMAIN = "/accounts/{accountId}/workers/scripts/{scriptName}/subdomain"
        const val SECRETS_BULK = "/accounts/{accountId}/workers/scripts/{scriptName}/secrets-bulk"
        const val DOMAINS = "/accounts/{accountId}/workers/domains"
        const val VERSIONS = "/accounts/{accountId}/workers/scripts/{scriptName}/versions"
        const val DOMAIN = "/accounts/{accountId}/workers/domains/{domainId}"
    }
}
