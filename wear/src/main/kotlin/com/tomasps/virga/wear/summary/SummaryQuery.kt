package com.tomasps.virga.wear.summary

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Duration
import java.time.Instant

/*
 * The account-level Workers requests and errors query. It deliberately duplicates the shape of Home's workers query
 * (feature/home analytics/AnalyticsQueries.kt) instead of depending on :feature:home: that module pulls the phone UI
 * through :core:ui, which the watch must not carry. Unlike Home it asks for one aggregated row (no datetime dimension),
 * so a busy account can never be cut short by the row limit and there is nothing to bucket.
 */

const val SUMMARY_OPERATION = "VirgaWearSummary"
const val SUMMARY_WINDOW_HOURS = 24L

val SUMMARY_QUERY = """
query $SUMMARY_OPERATION(${'$'}accountTag: string!, ${'$'}start: Time!, ${'$'}end: Time!) {
  viewer {
    accounts(filter: { accountTag: ${'$'}accountTag }) {
      totals: workersInvocationsAdaptive(limit: 1, filter: { datetime_geq: ${'$'}start, datetime_leq: ${'$'}end }) {
        sum { requests errors }
      }
    }
  }
}
""".trimIndent()

/** Variables for the 24 hours ending at [now]. */
fun summaryVariables(accountId: String, now: Instant): JsonObject = buildJsonObject {
    put("accountTag", accountId)
    put("start", now.minus(Duration.ofHours(SUMMARY_WINDOW_HOURS)).toString())
    put("end", now.toString())
}
