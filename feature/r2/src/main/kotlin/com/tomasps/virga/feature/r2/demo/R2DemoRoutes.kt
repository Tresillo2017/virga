package com.tomasps.virga.feature.r2.demo

import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.demo.DemoRouteContributor
import com.tomasps.virga.core.network.demo.DemoRouteRegistry
import com.tomasps.virga.core.network.demo.DemoRequest
import com.tomasps.virga.core.network.demo.InMemoryStore
import io.ktor.http.HttpStatusCode
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.longOrNull

/**
 * Canned R2 responses for the demo account. Bucket, object and rule data is seeded with neutral invented names.
 * Writes go to in-memory stores, so a created bucket or a deleted object shows up on the next read.
 */
class R2DemoRoutes(
    /** Stamps the creation date of a bucket made in this session. Injected so tests can pin it. */
    private val clock: () -> String = { Instant.now().toString() },
) : DemoRouteContributor {

    private val buckets = InMemoryStore(SEED_BUCKETS.associateBy { it.name })
    private val objects = InMemoryStore(SEED_OBJECTS.associateBy { it.storeKey })
    /** Bytes of objects uploaded in this session, so a demo download returns what was uploaded. */
    private val uploadedBodies = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()
    private val managedEnabled = InMemoryStore(mapOf(SEED_BUCKET_ASSETS to false))
    private val customDomains = InMemoryStore(mapOf(SEED_BUCKET_ASSETS to listOf(SEED_DOMAIN)))
    private val corsByBucket = InMemoryStore(mapOf(SEED_BUCKET_ASSETS to listOf(SEED_CORS)))
    private val lockByBucket = InMemoryStore(mapOf(SEED_BUCKET_BACKUPS to listOf(SEED_LOCK)))
    private val lifecycleByBucket = InMemoryStore(mapOf(SEED_BUCKET_LOGS to listOf(lifecycleJson())))

    override fun contribute(registry: DemoRouteRegistry) {
        contributeBuckets(registry)
        contributeObjects(registry)
        contributeSettings(registry)
        registry.graphQl(R2_BUCKET_METRICS_OPERATION) { request -> r2BucketMetricsReply(request) }
    }

    private fun contributeBuckets(registry: DemoRouteRegistry) {
        registry.get("/accounts/{accountId}/r2/buckets") { request ->
            val filter = request.query["name_contains"].orEmpty()
            val names = buckets.items.value.values.filter { filter.isEmpty() || it.name.contains(filter) }
            DemoReply.ok(
                result = buildJsonObject { put("buckets", buildJsonArray { names.forEach { add(bucketJson(it)) } }) },
                resultInfo = buildJsonObject { put("per_page", PAGE_SIZE) },
            )
        }
        registry.post("/accounts/{accountId}/r2/buckets") { request ->
            val body = parseBody(request)
            val name = body["name"]?.jsonPrimitive?.content.orEmpty()
            if (buckets.get(name) != null) {
                DemoReply.failure(HttpStatusCode.Conflict, CODE_BUCKET_EXISTS, "The bucket already exists")
            } else {
                val created = SeedBucket(
                    name = name,
                    location = body["locationHint"]?.jsonPrimitive?.content ?: DEFAULT_LOCATION,
                    storageClass = body["storageClass"]?.jsonPrimitive?.content ?: DEFAULT_STORAGE_CLASS,
                    creationDate = clock(),
                )
                buckets.put(name, created)
                DemoReply.ok(bucketJson(created))
            }
        }
        registry.get("/accounts/{accountId}/r2/buckets/{bucketName}") { request ->
            buckets.get(request.pathParams["bucketName"].orEmpty())?.let { DemoReply.ok(bucketJson(it)) } ?: notFound()
        }
        registry.delete("/accounts/{accountId}/r2/buckets/{bucketName}") { request ->
            deleteBucket(request.pathParams["bucketName"].orEmpty())
        }
    }

    private fun deleteBucket(name: String): DemoReply {
        if (buckets.get(name) == null) return notFound()
        val hasObjects = objects.items.value.values.any { it.bucket == name }
        if (hasObjects) {
            return DemoReply.failure(HttpStatusCode.Conflict, CODE_BUCKET_NOT_EMPTY, "The bucket is not empty")
        }
        buckets.remove(name)
        return DemoReply.ok(JsonObject(emptyMap()))
    }

    private fun contributeObjects(registry: DemoRouteRegistry) {
        registry.get("/accounts/{accountId}/r2/buckets/{bucketName}/objects") { request ->
            listObjects(request.pathParams["bucketName"].orEmpty(), request.query)
        }
        registry.get("/accounts/{accountId}/r2/buckets/{bucketName}/objects/{objectKey...}") { request ->
            val stored = objects.get(storeKey(request))
            if (stored == null) {
                notFound()
            } else {
                DemoReply.raw(uploadedBodies[storeKey(request)] ?: demoBody(stored.size), contentTypeOf(stored))
            }
        }
        registry.put("/accounts/{accountId}/r2/buckets/{bucketName}/objects/{objectKey...}") { request ->
            val key = storeKey(request)
            val bytes = request.bodyBytes ?: ByteArray(0)
            uploadedBodies[key] = bytes
            // The demo handler cannot see request headers, so the type is taken from the key's extension.
            val contentType = demoContentTypeFor(objectKeyOf(request))
            objects.put(
                key,
                SeedObject(
                    bucket = request.pathParams["bucketName"].orEmpty(),
                    key = objectKeyOf(request),
                    size = bytes.size.toLong(),
                    contentType = contentType,
                    metadata = emptyMap(),
                    lastModified = clock(),
                ),
            )
            DemoReply.ok(JsonObject(emptyMap()))
        }
        registry.delete("/accounts/{accountId}/r2/buckets/{bucketName}/objects/{objectKey...}") { request ->
            objects.remove(storeKey(request))
            uploadedBodies.remove(storeKey(request))
            DemoReply.empty()
        }
    }

    private fun listObjects(bucket: String, query: Map<String, String>): DemoReply {
        val prefix = query["prefix"].orEmpty()
        val delimited = query["delimiter"] == "/"
        val inBucket = objects.items.value.values
            .filter { it.bucket == bucket && it.key.startsWith(prefix) }
            .sortedBy { it.key }
        val folders = if (delimited) {
            inBucket.mapNotNull { folderOf(it.key, prefix) }.distinct()
        } else {
            emptyList()
        }
        val files = if (delimited) inBucket.filter { !it.key.removePrefix(prefix).contains('/') } else inBucket
        return DemoReply.ok(
            result = buildJsonArray { files.forEach { add(objectJson(it)) } },
            resultInfo = buildJsonObject {
                put("is_truncated", false)
                put("per_page", PAGE_SIZE)
                put("delimited", buildJsonArray { folders.forEach { add(JsonPrimitive(it)) } })
            },
        )
    }

    private fun contributeSettings(registry: DemoRouteRegistry) {
        registry.get("/accounts/{accountId}/r2/buckets/{bucketName}/domains/managed") { request ->
            val bucket = request.pathParams["bucketName"].orEmpty()
            DemoReply.ok(managedDomainJson(bucket, managedEnabled.get(bucket) == true))
        }
        registry.put("/accounts/{accountId}/r2/buckets/{bucketName}/domains/managed") { request ->
            val bucket = request.pathParams["bucketName"].orEmpty()
            val enabled = parseBody(request)["enabled"]?.jsonPrimitive?.content == "true"
            managedEnabled.put(bucket, enabled)
            DemoReply.ok(managedDomainJson(bucket, enabled))
        }
        registry.get("/accounts/{accountId}/r2/buckets/{bucketName}/domains/custom") { request ->
            val domains = customDomains.get(request.pathParams["bucketName"].orEmpty()).orEmpty()
            DemoReply.ok(buildJsonObject { put("domains", buildJsonArray { domains.forEach { add(domainJson(it)) } }) })
        }
        registry.post("/accounts/{accountId}/r2/buckets/{bucketName}/domains/custom") { request ->
            val bucket = request.pathParams["bucketName"].orEmpty()
            val body = parseBody(request)
            val domain = SeedDomain(
                domain = body["domain"]?.jsonPrimitive?.content.orEmpty(),
                zoneId = body["zoneId"]?.jsonPrimitive?.content,
                minTls = body["minTLS"]?.jsonPrimitive?.content,
            )
            customDomains.put(bucket, customDomains.get(bucket).orEmpty() + domain)
            DemoReply.ok(domainJson(domain))
        }
        registry.delete("/accounts/{accountId}/r2/buckets/{bucketName}/domains/custom/{domain}") { request ->
            val bucket = request.pathParams["bucketName"].orEmpty()
            val domain = request.pathParams["domain"].orEmpty()
            customDomains.put(bucket, customDomains.get(bucket).orEmpty().filterNot { it.domain == domain })
            DemoReply.empty()
        }
        registry.get("/accounts/{accountId}/r2/buckets/{bucketName}/cors") { request ->
            corsResult(request.pathParams["bucketName"].orEmpty())
        }
        registry.put("/accounts/{accountId}/r2/buckets/{bucketName}/cors") { request ->
            val bucket = request.pathParams["bucketName"].orEmpty()
            corsByBucket.put(bucket, parseCorsRules(parseBody(request)))
            DemoReply.ok(JsonObject(emptyMap()))
        }
        registry.delete("/accounts/{accountId}/r2/buckets/{bucketName}/cors") { request ->
            corsByBucket.remove(request.pathParams["bucketName"].orEmpty())
            DemoReply.empty()
        }
        registry.get("/accounts/{accountId}/r2/buckets/{bucketName}/lock") { request ->
            val rules = lockByBucket.get(request.pathParams["bucketName"].orEmpty()).orEmpty()
            DemoReply.ok(buildJsonObject { put("rules", buildJsonArray { rules.forEach { add(lockJson(it)) } }) })
        }
        registry.put("/accounts/{accountId}/r2/buckets/{bucketName}/lock") { request ->
            val bucket = request.pathParams["bucketName"].orEmpty()
            lockByBucket.put(bucket, parseLockRules(parseBody(request)))
            DemoReply.ok(JsonObject(emptyMap()))
        }
        registry.get("/accounts/{accountId}/r2/buckets/{bucketName}/lifecycle") { request ->
            val rules = lifecycleByBucket.get(request.pathParams["bucketName"].orEmpty()).orEmpty()
            DemoReply.ok(buildJsonObject { put("rules", buildJsonArray { rules.forEach { add(it) } }) })
        }
        registry.put("/accounts/{accountId}/r2/buckets/{bucketName}/lifecycle") { request ->
            val bucket = request.pathParams["bucketName"].orEmpty()
            val rules = (parseBody(request)["rules"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            lifecycleByBucket.put(bucket, rules)
            DemoReply.ok(JsonObject(emptyMap()))
        }
    }

    private fun corsResult(bucket: String): DemoReply {
        val rules = corsByBucket.get(bucket).orEmpty()
        return DemoReply.ok(buildJsonObject {
            put("rules", buildJsonArray {
                rules.forEach { rule ->
                    add(buildJsonObject {
                        rule.id?.let { put("id", it) }
                        put("allowed", buildJsonObject {
                            put("origins", stringArray(rule.origins))
                            put("methods", stringArray(rule.methods))
                        })
                        rule.maxAgeSeconds?.let { put("maxAgeSeconds", it) }
                    })
                }
            })
        })
    }

    private fun parseCorsRules(body: JsonObject): List<SeedCors> {
        val rules = body["rules"] as? JsonArray ?: return emptyList()
        return rules.mapNotNull { element ->
            val rule = element as? JsonObject ?: return@mapNotNull null
            val allowed = rule["allowed"] as? JsonObject
            SeedCors(
                id = rule["id"]?.jsonPrimitive?.content,
                origins = allowed?.get("origins").strings(),
                methods = allowed?.get("methods").strings(),
                maxAgeSeconds = rule["maxAgeSeconds"]?.jsonPrimitive?.content?.toIntOrNull(),
            )
        }
    }

    private fun parseLockRules(body: JsonObject): List<SeedLock> {
        val rules = body["rules"] as? JsonArray ?: return emptyList()
        return rules.mapNotNull { element ->
            val rule = element as? JsonObject ?: return@mapNotNull null
            val condition = rule["condition"] as? JsonObject
            SeedLock(
                id = rule["id"]?.jsonPrimitive?.content.orEmpty(),
                prefix = rule["prefix"]?.jsonPrimitive?.content,
                type = condition?.get("type")?.jsonPrimitive?.content.orEmpty(),
                maxAgeSeconds = condition?.get("maxAgeSeconds")?.jsonPrimitive?.longOrNull,
            )
        }
    }

    private fun parseBody(request: DemoRequest): JsonObject =
        request.bodyText?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() } ?: JsonObject(emptyMap())

    private fun storeKey(request: DemoRequest): String =
        request.pathParams["bucketName"].orEmpty() + "/" + objectKeyOf(request)

    private fun objectKeyOf(request: DemoRequest): String = request.pathParams["objectKey"].orEmpty()

    /**
     * Demo body for a stored object. The body is capped so a large seeded object does not allocate its full listed
     * size in memory; the listed size is unchanged, so only the bytes a demo download delivers are shorter.
     */
    private fun demoBody(listedSize: Long): ByteArray {
        val length = minOf(listedSize, DEMO_BODY_MAX_BYTES).toInt()
        return ByteArray(length) { DEMO_FILL_BYTE }
    }

    private fun contentTypeOf(stored: SeedObject): io.ktor.http.ContentType =
        stored.contentType?.let { runCatching { io.ktor.http.ContentType.parse(it) }.getOrNull() }
            ?: io.ktor.http.ContentType.Application.OctetStream

    private fun folderOf(key: String, prefix: String): String? {
        val rest = key.removePrefix(prefix)
        return if (rest.contains('/')) prefix + rest.substringBefore('/') + "/" else null
    }

    private fun notFound(): DemoReply = DemoReply.failure(HttpStatusCode.NotFound, CODE_NOT_FOUND, "Not found")

    private fun bucketJson(bucket: SeedBucket): JsonObject = buildJsonObject {
        put("name", bucket.name)
        put("creation_date", bucket.creationDate)
        put("location", bucket.location)
        put("storage_class", bucket.storageClass)
    }

    private fun objectJson(item: SeedObject): JsonObject = buildJsonObject {
        put("key", item.key)
        put("size", item.size)
        put("etag", "demo-etag-${item.key.hashCode().toUInt()}")
        put("last_modified", item.lastModified)
        put("storage_class", "Standard")
        put("custom_metadata", buildJsonObject { item.metadata.forEach { (k, v) -> put(k, v) } })
        put("http_metadata", buildJsonObject { item.contentType?.let { put("contentType", it) } })
    }

    /** The r2.dev answer: the public hostname is listed only while public access is on. */
    private fun managedDomainJson(bucket: String, enabled: Boolean): JsonObject = buildJsonObject {
        put("enabled", enabled)
        if (enabled) put("domain", "pub-$bucket.r2.dev")
    }

    private fun domainJson(domain: SeedDomain): JsonObject = buildJsonObject {
        put("domain", domain.domain)
        put("enabled", true)
        domain.zoneId?.let { put("zoneId", it) }
        domain.minTls?.let { put("minTLS", it) }
        put("status", buildJsonObject { put("ownership", "active"); put("ssl", "active") })
    }

    private fun lockJson(rule: SeedLock): JsonObject = buildJsonObject {
        put("id", rule.id)
        put("enabled", true)
        rule.prefix?.let { put("prefix", it) }
        put("condition", buildJsonObject {
            put("type", rule.type)
            rule.maxAgeSeconds?.let { put("maxAgeSeconds", it) }
        })
    }

    private fun lifecycleJson(): JsonObject = buildJsonObject {
        put("id", SEED_LIFECYCLE_ID)
        put("enabled", true)
        put("conditions", buildJsonObject { put("prefix", SEED_LOGS_PREFIX) })
        put("deleteObjectsTransition", buildJsonObject {
            put("condition", buildJsonObject { put("type", "Age"); put("maxAge", SEED_LOG_RETENTION_SECONDS) })
        })
    }

    private fun stringArray(values: List<String>): JsonArray = buildJsonArray { values.forEach { add(JsonPrimitive(it)) } }

    private fun JsonElement?.strings(): List<String> =
        (this as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty()

    private data class SeedBucket(val name: String, val location: String, val storageClass: String, val creationDate: String)

    private data class SeedObject(
        val bucket: String,
        val key: String,
        val size: Long,
        val contentType: String?,
        val metadata: Map<String, String>,
        val lastModified: String = SEED_CREATED_ON,
    ) {
        val storeKey: String get() = "$bucket/$key"
    }

    private data class SeedDomain(val domain: String, val zoneId: String?, val minTls: String?)

    private data class SeedCors(val id: String?, val origins: List<String>, val methods: List<String>, val maxAgeSeconds: Int?)

    private data class SeedLock(val id: String, val prefix: String?, val type: String, val maxAgeSeconds: Long?)

    private companion object {
        const val PAGE_SIZE = 50
        const val DEMO_BODY_MAX_BYTES = 1L shl 20
        const val DEMO_FILL_BYTE = 0x78.toByte()
        const val CODE_NOT_FOUND = 10006
        const val CODE_BUCKET_EXISTS = 10004
        const val CODE_BUCKET_NOT_EMPTY = 10008
        const val DEFAULT_LOCATION = "enam"
        const val DEFAULT_STORAGE_CLASS = "Standard"
        const val SEED_CREATED_ON = "2026-01-10T09:00:00Z"
        const val SEED_BUCKET_ASSETS = "demo-assets"
        const val SEED_BUCKET_BACKUPS = "demo-backups"
        const val SEED_BUCKET_LOGS = "demo-logs"
        const val SEED_LIFECYCLE_ID = "expire-app-logs"
        const val SEED_LOGS_PREFIX = "app/"
        const val SEED_LOG_RETENTION_SECONDS = 30L * 86_400L
        const val SEED_LOCK_RETENTION_SECONDS = 90L * 86_400L

        private val SEED_BUCKETS = listOf(
            SeedBucket(SEED_BUCKET_ASSETS, "enam", "Standard", SEED_CREATED_ON),
            SeedBucket(SEED_BUCKET_BACKUPS, "weur", "InfrequentAccess", SEED_CREATED_ON),
            SeedBucket(SEED_BUCKET_LOGS, "enam", "Standard", SEED_CREATED_ON),
        )

        private val SEED_OBJECTS = listOf(
            SeedObject(SEED_BUCKET_ASSETS, "index.html", 2_048, "text/html", emptyMap()),
            SeedObject(SEED_BUCKET_ASSETS, "css/site.css", 1_024, "text/css", emptyMap()),
            SeedObject(SEED_BUCKET_ASSETS, "css/theme.css", 512, "text/css", emptyMap()),
            SeedObject(SEED_BUCKET_ASSETS, "img/logo.png", 20_480, "image/png", mapOf("owner" to "design-team")),
            SeedObject(SEED_BUCKET_ASSETS, "img/hero.jpg", 143_360, "image/jpeg", emptyMap()),
            SeedObject(SEED_BUCKET_BACKUPS, "2026-10/backup-01.tar", 52_428_800, "application/x-tar", emptyMap()),
            SeedObject(SEED_BUCKET_LOGS, "app/2026-11-10.log", 4_096, "text/plain", emptyMap()),
        )

        private val SEED_DOMAIN = SeedDomain("static.example.test", "zone-demo-1", "1.2")
        private val SEED_CORS = SeedCors("allow-app", listOf("https://app.example.test"), listOf("GET", "HEAD"), 600)
        private val SEED_LOCK = SeedLock("keep-90-days", null, "Age", SEED_LOCK_RETENTION_SECONDS)
    }
}
