package com.tomasps.virga.feature.d1.demo

import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.core.network.demo.DemoRequest
import com.tomasps.virga.core.network.demo.DemoRouteContributor
import com.tomasps.virga.core.network.demo.DemoRouteRegistry
import com.tomasps.virga.core.network.demo.InMemoryStore
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
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
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

private const val BASE = "/accounts/{accountId}/d1/database"
private const val ONE = "$BASE/{databaseId}"
private const val RAW = "$ONE/raw"
private const val BOOKMARK = "$ONE/time_travel/bookmark"
private const val RESTORE = "$ONE/time_travel/restore"
private const val EXPORT = "$ONE/export"
private const val UNDELETE = "$ONE/undelete"
private const val NOT_FOUND_CODE = 7003
private const val UNSUPPORTED_CODE = 7500
private const val DEMO_CREATED_ON = "2026-06-14T09:30:00Z"
private const val DEMO_ORDERS_ID = "demo-db-orders"
private const val DEMO_LEDGER_ID = "demo-db-ledger"
private const val LIST_PAGE_DEFAULT = 1
private const val LIST_PER_PAGE_DEFAULT = 50
private const val DEMO_BOOKMARK = "00000085-0000024c-00004c6d-demo"
private const val DEMO_PREVIOUS_BOOKMARK = "00000080-0000024c-00004c60-demo"
private const val DEMO_EXPORT_BOOKMARK = "00000086-0000024c-00004c70-demo"

/**
 * Canned D1 for demo mode. Two invented databases with tables that the demo SQL engine can read and write. Writes
 * update [databases], so the table grid changes after an insert, edit or delete. Time Travel restores and exports
 * answer with success but do not change data (they cannot be emulated honestly).
 */
internal class D1DemoRoutes(
    private val databases: InMemoryStore<DemoDatabase> = InMemoryStore(seedDatabases().associateBy { it.uuid }),
    private val deleted: InMemoryStore<DemoDatabase> = InMemoryStore(emptyMap()),
) : DemoRouteContributor {

    override fun contribute(registry: DemoRouteRegistry) {
        registry.get(BASE) { list(it) }
        registry.post(BASE) { create(it) }
        registry.get(ONE) { withDatabase(it) { db -> DemoReply.ok(databaseJson(db)) } }
        registry.put(ONE) { putReplication(it) }
        registry.delete(ONE) { remove(it) }
        registry.post(RAW) { raw(it) }
        registry.graphQl(D1_DAILY_OPERATION) { d1DailyReply(it) }
        registry.graphQl(D1_STORAGE_OPERATION) { d1StorageReply(it) }
        registry.get(BOOKMARK) { withDatabase(it) { DemoReply.ok(buildJsonObject { put("bookmark", DEMO_BOOKMARK) }) } }
        registry.post(RESTORE) { restore(it) }
        registry.post(EXPORT) { export(it) }
        registry.post(UNDELETE) { undelete(it) }
    }

    private fun list(request: DemoRequest): DemoReply {
        val page = request.query["page"]?.toIntOrNull() ?: LIST_PAGE_DEFAULT
        val perPage = request.query["per_page"]?.toIntOrNull() ?: LIST_PER_PAGE_DEFAULT
        val all = databases.items.value.values.sortedBy { it.name }
        val slice = all.drop((page - 1).coerceAtLeast(0) * perPage).take(perPage)
        val info = buildJsonObject {
            put("page", page)
            put("per_page", perPage)
            put("count", slice.size)
            put("total_count", all.size)
        }
        return DemoReply.ok(buildJsonArray { slice.forEach { add(databaseJson(it)) } }, resultInfo = info)
    }

    private fun create(request: DemoRequest): DemoReply {
        val body = parseObject(request.bodyText) ?: return invalidBody()
        val name = body["name"]?.jsonPrimitive?.content?.trim().orEmpty()
        if (name.isEmpty()) return DemoReply.failure(HttpStatusCode.BadRequest, 7400, "Database name is required")
        val uuid = "demo-db-${name.hashCode().toUInt()}"
        val created = DemoDatabase(
            uuid = uuid,
            name = name,
            createdAt = DEMO_CREATED_ON,
            jurisdiction = body["jurisdiction"]?.jsonPrimitive?.content,
            readReplicationMode = "disabled",
            tables = emptyMap(),
        )
        databases.put(uuid, created)
        return DemoReply.ok(databaseJson(created))
    }

    private fun putReplication(request: DemoRequest): DemoReply = withDatabase(request) { db ->
        val mode = parseObject(request.bodyText)?.get("read_replication")?.jsonObject?.get("mode")?.jsonPrimitive?.content
        if (mode != "auto" && mode != "disabled") return@withDatabase invalidBody()
        val updated = db.copy(readReplicationMode = mode)
        databases.put(db.uuid, updated)
        DemoReply.ok(databaseJson(updated))
    }

    /** Deleted databases are kept aside so the demo undelete can restore them. */
    private fun remove(request: DemoRequest): DemoReply = withDatabase(request) { db ->
        databases.remove(db.uuid)
        deleted.put(db.uuid, db)
        DemoReply.empty()
    }

    private fun undelete(request: DemoRequest): DemoReply {
        val id = request.pathParams["databaseId"].orEmpty()
        val db = deleted.get(id) ?: return DemoReply.failure(HttpStatusCode.NotFound, NOT_FOUND_CODE, "Database not found")
        deleted.remove(id)
        databases.put(db.uuid, db)
        return DemoReply.ok(databaseJson(db))
    }

    private fun raw(request: DemoRequest): DemoReply = withDatabase(request) { db ->
        val body = parseObject(request.bodyText) ?: return@withDatabase invalidBody()
        val sql = body["sql"]?.jsonPrimitive?.content.orEmpty()
        val params = body["params"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
        val outcome = try {
            DemoSqlEngine.run(db, sql, params)
        } catch (unsupported: DemoSqlUnsupported) {
            return@withDatabase DemoReply.failure(HttpStatusCode.BadRequest, UNSUPPORTED_CODE, UNSUPPORTED_MESSAGE)
        }
        outcome.updated?.let { databases.put(db.uuid, it) }
        DemoReply.ok(statementResults(outcome))
    }

    private fun restore(request: DemoRequest): DemoReply = withDatabase(request) {
        DemoReply.ok(buildJsonObject {
            put("bookmark", DEMO_BOOKMARK)
            put("previous_bookmark", DEMO_PREVIOUS_BOOKMARK)
            put("message", RESTORE_MESSAGE)
        })
    }

    /** First call starts the export and returns its bookmark. The next poll, carrying that bookmark, completes it. */
    private fun export(request: DemoRequest): DemoReply = withDatabase(request) { db ->
        val body = parseObject(request.bodyText).orEmpty()
        val polling = body["current_bookmark"] != null
        val result = if (!polling) {
            buildJsonObject { put("at_bookmark", DEMO_EXPORT_BOOKMARK); put("type", "export"); put("success", true) }
        } else {
            buildJsonObject {
                put("status", "complete")
                put("at_bookmark", DEMO_EXPORT_BOOKMARK)
                put("type", "export")
                put("success", true)
                putJsonObject("result") {
                    put("filename", "${db.name}.sql")
                    put("signed_url", "https://example.com/virga-demo/${db.uuid}.sql?sig=demo")
                }
            }
        }
        DemoReply.ok(result)
    }

    private inline fun withDatabase(request: DemoRequest, handle: (DemoDatabase) -> DemoReply): DemoReply {
        val id = request.pathParams["databaseId"].orEmpty()
        val db = databases.get(id) ?: return DemoReply.failure(HttpStatusCode.NotFound, NOT_FOUND_CODE, "Database not found")
        return handle(db)
    }

    private fun invalidBody() = DemoReply.failure(HttpStatusCode.BadRequest, 7400, "Request body is not valid JSON")

    private fun parseObject(text: String?): JsonObject? = text?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }

    private fun databaseJson(db: DemoDatabase): JsonObject = buildJsonObject {
        put("uuid", db.uuid)
        put("name", db.name)
        put("created_at", db.createdAt)
        db.jurisdiction?.let { put("jurisdiction", it) }
        put("file_size", db.sizeBytes)
        put("version", "production")
        putJsonObject("read_replication") { put("mode", db.readReplicationMode) }
    }

    private fun statementResults(outcome: DemoSqlResult): JsonArray = buildJsonArray {
        add(buildJsonObject {
            put("success", true)
            if (outcome.columns.isNotEmpty()) {
                putJsonObject("results") {
                    putJsonArray("columns") { outcome.columns.forEach { add(JsonPrimitive(it)) } }
                    putJsonArray("rows") { outcome.rows.forEach { row -> add(JsonArray(row)) } }
                }
            }
            putJsonObject("meta") {
                put("rows_read", outcome.rows.size)
                put("rows_written", outcome.rowsWritten)
                put("changes", outcome.rowsWritten)
                putJsonObject("timings") { put("sql_duration_ms", DEMO_SQL_DURATION_MS) }
            }
        })
    }

    private companion object {
        const val UNSUPPORTED_MESSAGE = "Demo mode runs only the statements the app sends and simple SELECTs on demo tables."
        const val RESTORE_MESSAGE = "Demo restore: the demo data was not changed."
        const val DEMO_SQL_DURATION_MS = 0.4
    }
}

/** The two demo databases. Table data is invented. */
internal fun seedDatabases(): List<DemoDatabase> = listOf(
    DemoDatabase(
        uuid = DEMO_ORDERS_ID,
        name = "orders-demo",
        createdAt = DEMO_CREATED_ON,
        jurisdiction = null,
        readReplicationMode = "auto",
        tables = mapOf(
            "customers" to DemoTable(
                columns = listOf(
                    DemoColumn("id", "INTEGER", pkPosition = 1),
                    DemoColumn("name", "TEXT", notNull = true),
                    DemoColumn("region", "TEXT", defaultValue = "'EU'"),
                ),
                rows = listOf(
                    listOf(JsonPrimitive(1), JsonPrimitive("Ada Lind"), JsonPrimitive("EU")),
                    listOf(JsonPrimitive(2), JsonPrimitive("Ben Ortiz"), JsonPrimitive("NA")),
                    listOf(JsonPrimitive(3), JsonPrimitive("Chloe Mwangi"), JsonPrimitive("APAC")),
                ),
            ),
            "orders" to DemoTable(
                columns = listOf(
                    DemoColumn("id", "INTEGER", pkPosition = 1),
                    DemoColumn("customer_id", "INTEGER", notNull = true),
                    DemoColumn("total_cents", "INTEGER", notNull = true),
                    DemoColumn("status", "TEXT", defaultValue = "'open'"),
                ),
                rows = listOf(
                    listOf(JsonPrimitive(101), JsonPrimitive(1), JsonPrimitive(4_900), JsonPrimitive("shipped")),
                    listOf(JsonPrimitive(102), JsonPrimitive(2), JsonPrimitive(12_500), JsonPrimitive("open")),
                    listOf(JsonPrimitive(103), JsonPrimitive(3), JsonPrimitive(800), JsonPrimitive("open")),
                ),
                indexes = listOf(DemoIndex("orders_status", unique = false, columns = listOf("status"))),
                foreignKeys = listOf(DemoForeignKey(from = "customer_id", table = "customers", to = "id", onDelete = "NO ACTION")),
            ),
            "audit_log" to DemoTable(
                columns = listOf(DemoColumn("message", "TEXT"), DemoColumn("at", "TEXT")),
                rows = listOf(
                    listOf(JsonPrimitive("seeded"), JsonPrimitive(DEMO_CREATED_ON)),
                ),
            ),
        ),
    ),
    DemoDatabase(
        uuid = DEMO_LEDGER_ID,
        name = "ledger-demo",
        createdAt = DEMO_CREATED_ON,
        jurisdiction = "eu",
        readReplicationMode = "disabled",
        tables = mapOf(
            "entries" to DemoTable(
                columns = listOf(
                    DemoColumn("id", "INTEGER", pkPosition = 1),
                    DemoColumn("account", "TEXT", notNull = true),
                    DemoColumn("amount_cents", "INTEGER", notNull = true),
                ),
                rows = listOf(
                    listOf(JsonPrimitive(1), JsonPrimitive("cash"), JsonPrimitive(20_000)),
                    listOf(JsonPrimitive(2), JsonPrimitive("revenue"), JsonPrimitive(-20_000)),
                ),
            ),
        ),
    ),
)
