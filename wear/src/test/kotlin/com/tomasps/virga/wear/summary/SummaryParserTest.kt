package com.tomasps.virga.wear.summary

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SummaryParserTest {

    private fun data(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    private fun account(totals: String) = data("""{"viewer":{"accounts":[{"totals":$totals}]}}""")

    @Test
    fun parsesRequestsAndErrors() {
        val parsed = parseSummary(account("""[{"sum":{"requests":1200,"errors":34}}]"""))

        assertEquals(SummaryData(requests = 1200, errors = 34), parsed)
    }

    @Test
    fun emptyResultIsZeroNotFailure() {
        assertEquals(SummaryData(0, 0), parseSummary(account("[]")))
        assertEquals(SummaryData(0, 0), parseSummary(data("""{"viewer":{"accounts":[]}}""")))
    }

    @Test
    fun missingViewerIsMalformedNotZero() {
        assertNull(parseSummary(null))
        assertNull(parseSummary(data("{}")))
    }

    @Test
    fun rowsAreAddedUp() {
        val parsed = parseSummary(account("""[{"sum":{"requests":10,"errors":1}},{"sum":{"requests":5,"errors":2}}]"""))

        assertEquals(SummaryData(15, 3), parsed)
    }

    @Test
    fun oddlyTypedOrMissingFieldsReadAsZero() {
        val parsed = parseSummary(account("""[{"sum":{"requests":"many"}},{"nothing":true}]"""))

        assertEquals(SummaryData(0, 0), parsed)
    }
}
