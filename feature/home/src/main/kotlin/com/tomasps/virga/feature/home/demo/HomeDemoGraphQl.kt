package com.tomasps.virga.feature.home.demo

import com.tomasps.virga.core.network.demo.DemoReply
import com.tomasps.virga.feature.home.analytics.WORKERS_OPERATION
import com.tomasps.virga.feature.home.analytics.ZONES_OPERATION
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

private val OPERATION = Regex("""query\s+(\w+)""")
private val ZONE_TAG_LIST = Regex("""zoneTag_in:\s*\[([^\]]*)\]""")
private val ZONE_TAG = Regex("""[0-9a-f]{32}""")

/**
 * Requests per Worker for the Home analytics. The names are the Workers feature's demo scripts, so every count lands on
 * a Worker the Workers list shows.
 */
internal val DEMO_SCRIPTS = listOf("edge-api" to 4_200L, "image-resizer" to 1_180L, "cron-cleanup" to 310L)

private const val BASE_WORKER_REQUESTS_PER_HOUR = 900.0
private const val BASE_ZONE_REQUESTS_PER_HOUR = 2_400.0
private const val WAVE_PERIOD_HOURS = 24.0
private const val DEMO_SEED_SALT = 7_919L
private const val CACHE_RATIO_MIN = 0.55
private const val CACHE_RATIO_SPAN = 0.35
private const val ERROR_RATIO = 0.02
private const val CPU_P50_MICROS = 2_100.0
private const val CPU_P99_MICROS = 18_500.0
private const val QUERY_ERROR = "Demo query is not supported"

/** Answers the Home GraphQL operations from canned, deterministic data. Other operations get a GraphQL error. */
internal fun homeDemoGraphQlReply(body: String?): DemoReply {
    val root = body?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
        ?: return DemoReply.graphQlErrors(QUERY_ERROR)
    val query = (root["query"] as? JsonPrimitive)?.content.orEmpty()
    val variables = root["variables"] as? JsonObject ?: JsonObject(emptyMap())
    return when (OPERATION.find(query)?.groupValues?.get(1)) {
        WORKERS_OPERATION -> DemoReply.graphQl(workersData(variables))
        ZONES_OPERATION -> DemoReply.graphQl(zonesData(query, variables))
        else -> DemoReply.graphQlErrors(QUERY_ERROR)
    }
}

private fun workersData(variables: JsonObject): JsonObject {
    val start = variables.instant("start") ?: return emptyAccountData()
    val split = variables.instant("split") ?: start
    val end = variables.instant("end") ?: split
    val rng = Random(start.epochSecond + DEMO_SEED_SALT)
    val hours = hoursBetween(start, end)
    val hourly = buildJsonArray {
        hours.forEachIndexed { index, at ->
            add(buildJsonObject {
                put("dimensions", buildJsonObject { put("datetime", at.toString()) })
                val requests = waveValue(BASE_WORKER_REQUESTS_PER_HOUR * DEMO_SCRIPTS.size, index, rng)
                put("sum", buildJsonObject {
                    put("requests", requests.toLong())
                    put("errors", (requests * ERROR_RATIO).toLong())
                })
            })
        }
    }
    val byScript = buildJsonArray {
        DEMO_SCRIPTS.forEach { (name, requests) ->
            add(buildJsonObject {
                put("dimensions", buildJsonObject { put("scriptName", name) })
                put("sum", buildJsonObject { put("requests", requests) })
            })
        }
    }
    return accountData(
        hourly = hourly,
        currentCpu = cpuNode(),
        previousCpu = cpuNode(),
        byScript = byScript,
    )
}

private fun zonesData(query: String, variables: JsonObject): JsonObject {
    val start = variables.instant("start") ?: return emptyAccountData()
    val end = variables.instant("end") ?: start
    val tags = ZONE_TAG_LIST.find(query)?.groupValues?.get(1).orEmpty()
        .let { list -> ZONE_TAG.findAll(list).map { it.value }.toList() }
    val hours = hoursBetween(start, end)
    val zones = buildJsonArray {
        tags.forEachIndexed { zoneIndex, tag ->
            val rng = Random(start.epochSecond + tag.hashCode())
            val zoneBase = BASE_ZONE_REQUESTS_PER_HOUR * (1.0 + zoneIndex * 0.5)
            add(buildJsonObject {
                put("zoneTag", tag)
                put("hourly", buildJsonArray {
                    hours.forEachIndexed { index, at ->
                        val requests = waveValue(zoneBase, index, rng)
                        val cached = requests * (CACHE_RATIO_MIN + rng.nextDouble() * CACHE_RATIO_SPAN)
                        add(buildJsonObject {
                            put("dimensions", buildJsonObject { put("datetime", at.toString()) })
                            put("sum", buildJsonObject {
                                put("requests", requests.toLong())
                                put("cachedRequests", cached.toLong())
                            })
                        })
                    }
                })
            })
        }
    }
    return accountData(zones = zones)
}

private fun accountData(
    hourly: JsonArray = JsonArray(emptyList()),
    currentCpu: JsonArray = JsonArray(emptyList()),
    previousCpu: JsonArray = JsonArray(emptyList()),
    byScript: JsonArray = JsonArray(emptyList()),
    zones: JsonArray = JsonArray(emptyList()),
): JsonObject = buildJsonObject {
    put("viewer", buildJsonObject {
        put("accounts", buildJsonArray {
            add(buildJsonObject {
                put("hourly", hourly)
                put("currentCpu", currentCpu)
                put("previousCpu", previousCpu)
                put("byScript", byScript)
                put("zones", zones)
            })
        })
    })
}

private fun emptyAccountData(): JsonObject = accountData()

private fun cpuNode(): JsonArray = buildJsonArray {
    add(buildJsonObject {
        put("quantiles", buildJsonObject {
            put("cpuTimeP50", CPU_P50_MICROS)
            put("cpuTimeP99", CPU_P99_MICROS)
        })
    })
}

private fun waveValue(base: Double, index: Int, rng: Random): Double {
    val wave = 1.0 + 0.3 * sin(2 * PI * index / WAVE_PERIOD_HOURS)
    val jitter = 0.9 + rng.nextDouble() * 0.2
    return base * wave * jitter
}

private fun hoursBetween(start: Instant, end: Instant): List<Instant> {
    val first = start.truncatedTo(ChronoUnit.HOURS)
    return generateSequence(first) { it.plus(1, ChronoUnit.HOURS) }
        .takeWhile { it < end }
        .toList()
}

private fun JsonObject.instant(key: String): Instant? =
    (this[key] as? JsonPrimitive)?.content?.let { runCatching { Instant.parse(it) }.getOrNull() }
