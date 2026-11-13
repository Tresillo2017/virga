package com.tomasps.virga.feature.kv.demo

import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.demo.DemoRequest
import com.tomasps.virga.core.network.demo.DemoRouteContributor
import com.tomasps.virga.core.network.demo.DemoRouteRegistry
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

private const val BASE = "/accounts/{accountId}/storage/kv/namespaces"
private const val NS = "$BASE/{namespaceId}"
private const val VALUE = "$NS/values/{key...}"
private const val NOT_FOUND_CODE = 7003
private const val DEFAULT_NAMESPACE_PAGE = 20
private const val MAX_NAMESPACE_PAGE = 1000
private const val MIN_KEY_PAGE = 10
private const val MAX_KEY_PAGE = 1000
private const val SECONDS_PER_MILLI = 1_000L
private const val NAMESPACE_ID_WIDTH = 32

/**
 * Canned storage/kv routes for the demo account. Reads come from [KvDemoStore]; writes change it, so a list shows what
 * was just saved. Only the storage/kv paths are answered, because the legacy workers/namespaces routes are retired.
 */
internal class KvDemoRoutes(
    private val store: KvDemoStore,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / SECONDS_PER_MILLI },
) : DemoRouteContributor {

    private var nextNamespaceCounter = 0L

    override fun contribute(registry: DemoRouteRegistry) {
        registry.get(BASE) { request -> listNamespaces(request) }
        registry.post(BASE) { request -> createNamespace(request) }
        registry.get(NS) { request -> withNamespace(request) { ns -> DemoReply.ok(namespaceJson(ns)) } }
        registry.put(NS) { request -> renameNamespace(request) }
        registry.delete(NS) { request -> deleteNamespace(request) }
        registry.get("$NS/keys") { request -> listKeys(request) }
        registry.get(VALUE) { request -> readValue(request) }
        registry.get("$NS/metadata/{key...}") { request -> readMetadata(request) }
        registry.put(VALUE) { request -> writeValue(request) }
        registry.delete(VALUE) { request -> deleteValue(request) }
        registry.put("$NS/bulk") { request -> bulkWrite(request) }
        registry.post("$NS/bulk/delete") { request -> bulkDelete(request) }
    }

    private fun listNamespaces(request: DemoRequest): DemoReply {
        val account = request.pathParams["accountId"].orEmpty()
        if (!isDemoAccount(account)) return notFound()
        val all = namespacesOf(account).sortedBy { it.title }
        val perPage = request.query["per_page"]?.toIntOrNull()?.coerceIn(1, MAX_NAMESPACE_PAGE) ?: DEFAULT_NAMESPACE_PAGE
        val page = request.query["page"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val slice = all.drop((page - 1) * perPage).take(perPage)
        val resultInfo = buildJsonObject {
            put("page", page)
            put("per_page", perPage)
            put("count", slice.size)
            put("total_count", all.size)
        }
        return DemoReply.ok(JsonArray(slice.map { namespaceJson(it) }), resultInfo)
    }

    private fun createNamespace(request: DemoRequest): DemoReply {
        val account = request.pathParams["accountId"].orEmpty()
        if (!isDemoAccount(account)) return notFound()
        val title = titleFrom(request.bodyText) ?: return badRequest()
        val id = newNamespaceId()
        val namespace = KvDemoNamespace(id = id, title = title)
        store.namespaces.put(store.namespaceKey(account, id), namespace)
        return DemoReply.ok(namespaceJson(namespace))
    }

    private fun renameNamespace(request: DemoRequest): DemoReply {
        val account = request.pathParams["accountId"].orEmpty()
        val nsId = request.pathParams["namespaceId"].orEmpty()
        val title = titleFrom(request.bodyText) ?: return badRequest()
        return withNamespace(request) { ns ->
            store.namespaces.put(store.namespaceKey(account, nsId), ns.copy(title = title))
            DemoReply.ok(namespaceJson(ns.copy(title = title)))
        }
    }

    private fun deleteNamespace(request: DemoRequest): DemoReply {
        val account = request.pathParams["accountId"].orEmpty()
        val nsId = request.pathParams["namespaceId"].orEmpty()
        return withNamespace(request) {
            store.namespaces.remove(store.namespaceKey(account, nsId))
            val prefix = store.keyPrefix(account, nsId)
            store.entries.items.value.keys.filter { it.startsWith(prefix) }.forEach { store.entries.remove(it) }
            DemoReply.ok(buildJsonObject { })
        }
    }

    private fun listKeys(request: DemoRequest): DemoReply = withNamespace(request) { _ ->
        val account = request.pathParams["accountId"].orEmpty()
        val nsId = request.pathParams["namespaceId"].orEmpty()
        val prefix = request.query["prefix"].orEmpty()
        val names = entryNames(account, nsId).filter { it.startsWith(prefix) }
        val limit = request.query["limit"]?.toIntOrNull()?.coerceIn(MIN_KEY_PAGE, MAX_KEY_PAGE) ?: MAX_KEY_PAGE
        val offset = request.query["cursor"]?.toIntOrNull() ?: 0
        val page = names.drop(offset).take(limit)
        val next = offset + page.size
        val resultInfo = buildJsonObject {
            put("count", page.size)
            if (next < names.size) put("cursor", next.toString())
        }
        DemoReply.ok(JsonArray(page.map { keyJson(account, nsId, it) }), resultInfo)
    }

    private fun readValue(request: DemoRequest): DemoReply = withEntry(request) { entry ->
        DemoReply.raw(entry.bytes)
    }

    private fun readMetadata(request: DemoRequest): DemoReply = withEntry(request) { entry ->
        DemoReply.ok(entry.metadata ?: JsonNull)
    }

    private fun writeValue(request: DemoRequest): DemoReply = withNamespace(request) { _ ->
        val account = request.pathParams["accountId"].orEmpty()
        val nsId = request.pathParams["namespaceId"].orEmpty()
        val key = request.pathParams["key"].orEmpty()
        val write = parseKvWriteBody(request.bodyBytes ?: ByteArray(0))
        val expiresAt = expiresAtOf(request.query, nowEpochSeconds())
        store.entries.put(
            store.entryKey(account, nsId, key),
            KvDemoEntry(write.value, write.metadata, expiresAt),
        )
        DemoReply.ok(JsonNull)
    }

    private fun deleteValue(request: DemoRequest): DemoReply = withNamespace(request) { _ ->
        val account = request.pathParams["accountId"].orEmpty()
        val nsId = request.pathParams["namespaceId"].orEmpty()
        val key = request.pathParams["key"].orEmpty()
        store.entries.remove(store.entryKey(account, nsId, key))
        DemoReply.empty()
    }

    private fun bulkWrite(request: DemoRequest): DemoReply = withNamespace(request) { _ ->
        val account = request.pathParams["accountId"].orEmpty()
        val nsId = request.pathParams["namespaceId"].orEmpty()
        val pairs = parseKvBulkPairs(request.bodyText.orEmpty(), nowEpochSeconds())
        pairs.forEach { pair ->
            store.entries.put(store.entryKey(account, nsId, pair.key), KvDemoEntry(pair.value, pair.metadata, pair.expiresAt))
        }
        DemoReply.ok(bulkResult(pairs.size))
    }

    private fun bulkDelete(request: DemoRequest): DemoReply = withNamespace(request) { _ ->
        val account = request.pathParams["accountId"].orEmpty()
        val nsId = request.pathParams["namespaceId"].orEmpty()
        val names = parseKvDeleteNames(request.bodyText.orEmpty())
        names.forEach { store.entries.remove(store.entryKey(account, nsId, it)) }
        DemoReply.ok(bulkResult(names.size))
    }

    /** Runs [block] when the account is a demo account and the namespace exists; otherwise answers not found. */
    private inline fun withNamespace(request: DemoRequest, block: (KvDemoNamespace) -> DemoReply): DemoReply {
        val account = request.pathParams["accountId"].orEmpty()
        val nsId = request.pathParams["namespaceId"].orEmpty()
        if (!isDemoAccount(account)) return notFound()
        val namespace = store.namespaces.get(store.namespaceKey(account, nsId)) ?: return notFound()
        return block(namespace)
    }

    /** Runs [block] when the key exists in the namespace; otherwise answers not found. */
    private inline fun withEntry(request: DemoRequest, block: (KvDemoEntry) -> DemoReply): DemoReply =
        withNamespace(request) { _ ->
            val account = request.pathParams["accountId"].orEmpty()
            val nsId = request.pathParams["namespaceId"].orEmpty()
            val key = request.pathParams["key"].orEmpty()
            val entry = store.entries.get(store.entryKey(account, nsId, key)) ?: return@withNamespace notFound()
            block(entry)
        }

    private fun namespacesOf(account: String): List<KvDemoNamespace> {
        val prefix = account + KV_DEMO_SEP
        return store.namespaces.items.value.filterKeys { it.startsWith(prefix) }.values.toList()
    }

    private fun entryNames(account: String, nsId: String): List<String> {
        val prefix = store.keyPrefix(account, nsId)
        return store.entries.items.value.keys
            .filter { it.startsWith(prefix) }
            .map { it.removePrefix(prefix) }
            .sorted()
    }

    private fun keyJson(account: String, nsId: String, name: String): JsonObject {
        val entry = store.entries.get(store.entryKey(account, nsId, name))
        return buildJsonObject {
            put("name", name)
            entry?.expiresAtEpochSeconds?.let { put("expiration", it) }
            entry?.metadata?.let { put("metadata", it) }
        }
    }

    private fun newNamespaceId(): String {
        nextNamespaceCounter += 1
        return nextNamespaceCounter.toString(16).padStart(NAMESPACE_ID_WIDTH, '0')
    }

    private fun isDemoAccount(accountId: String) = accountId == KV_DEMO_ACCOUNT_PERSONAL || accountId == KV_DEMO_ACCOUNT_STAGING

    private fun notFound() = DemoReply.failure(HttpStatusCode.NotFound, NOT_FOUND_CODE, "Not found in the demo data")

    private fun badRequest() = DemoReply.failure(HttpStatusCode.BadRequest, NOT_FOUND_CODE, "Invalid request body")

    private fun titleFrom(text: String?): String? {
        val obj = runCatching { Json.parseToJsonElement(text.orEmpty()) as? JsonObject }.getOrNull() ?: return null
        return (obj["title"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
    }

    private fun namespaceJson(ns: KvDemoNamespace): JsonObject = buildJsonObject {
        put("id", ns.id)
        put("title", ns.title)
    }

    private fun bulkResult(successful: Int): JsonElement = buildJsonObject {
        put("successful_key_count", successful)
        putJsonArray("unsuccessful_keys") {}
    }
}
