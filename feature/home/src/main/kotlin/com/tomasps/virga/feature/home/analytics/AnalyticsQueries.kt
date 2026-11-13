package com.tomasps.virga.feature.home.analytics

/*
 * GraphQL Analytics API queries. Each operation has its own name so a demo dispatcher can answer it.
 * Node and field names come from the GraphQL Analytics API documentation:
 * workersInvocationsAdaptive (sum requests/errors, quantiles cpuTimeP50/cpuTimeP99, dimension datetime, scriptName)
 * and httpRequests1hGroups under zones (sum requests/cachedRequests, dimension datetime).
 * The zone field names and the zoneTag field on the zone node are not yet confirmed against the live schema.
 */

const val WORKERS_OPERATION = "VirgaHomeWorkers"
const val ZONES_OPERATION = "VirgaHomeZones"

/** Rows requested per grouped node. Two comparable periods of hourly rows fit comfortably under this. */
const val MAX_HOURLY_ROWS = 1000

/** Upper bound on zones read on Home. Each query takes at most 10 zones, so this caps the GraphQL budget. */
const val MAX_ZONES_ON_HOME = 50

/** Zones per zone-scoped query. The GraphQL limits page allows up to 10 zones in one zone-scoped query. */
const val ZONES_PER_QUERY = 10

/** Hourly rows and the shortcut list size used by the Workers node. */
const val TOP_SCRIPT_LIMIT = 100

val WORKERS_QUERY = """
query $WORKERS_OPERATION(${'$'}accountTag: string!, ${'$'}start: Time!, ${'$'}split: Time!, ${'$'}splitBefore: Time!, ${'$'}end: Time!) {
  viewer {
    accounts(filter: { accountTag: ${'$'}accountTag }) {
      hourly: workersInvocationsAdaptive(limit: $MAX_HOURLY_ROWS, filter: { datetime_geq: ${'$'}start, datetime_leq: ${'$'}end }) {
        dimensions { datetime }
        sum { requests errors }
      }
      currentCpu: workersInvocationsAdaptive(limit: 1, filter: { datetime_geq: ${'$'}split, datetime_leq: ${'$'}end }) {
        quantiles { cpuTimeP50 cpuTimeP99 }
      }
      previousCpu: workersInvocationsAdaptive(limit: 1, filter: { datetime_geq: ${'$'}start, datetime_leq: ${'$'}splitBefore }) {
        quantiles { cpuTimeP50 cpuTimeP99 }
      }
      byScript: workersInvocationsAdaptive(limit: $TOP_SCRIPT_LIMIT, filter: { datetime_geq: ${'$'}split, datetime_leq: ${'$'}end }) {
        dimensions { scriptName }
        sum { requests }
      }
    }
  }
}
""".trimIndent()

/** Builds the zones query. [zoneTags] must already be validated as 32-character hex ids. */
fun zonesQuery(zoneTags: List<String>): String {
    val list = zoneTags.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
    return """
query $ZONES_OPERATION(${'$'}accountTag: string!, ${'$'}start: Time!, ${'$'}end: Time!) {
  viewer {
    accounts(filter: { accountTag: ${'$'}accountTag }) {
      zones(filter: { zoneTag_in: $list }) {
        zoneTag
        hourly: httpRequests1hGroups(limit: $MAX_HOURLY_ROWS, filter: { datetime_geq: ${'$'}start, datetime_lt: ${'$'}end }) {
          dimensions { datetime }
          sum { requests cachedRequests }
        }
      }
    }
  }
}
""".trimIndent()
}
