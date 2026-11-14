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
    fun noTrafficIsARealZero() {
        assertEquals(SummaryData(0, 0), parseSummary(account("[]")))
        assertEquals(SummaryData(0, 0), parseSummary(account("""[{"sum":{"requests":0,"errors":0}}]""")))
    }

    @Test
    fun missingViewerIsMalformedNotZero() {
        assertNull(parseSummary(null))
        assertNull(parseSummary(data("{}")))
    }

    @Test
    fun missingOrEmptyAccountsIsMalformedNotZero() {
        assertNull(parseSummary(data("""{"viewer":{}}""")))
        assertNull(parseSummary(data("""{"viewer":{"accounts":[]}}""")))
        assertNull(parseSummary(data("""{"viewer":{"accounts":[null]}}""")))
    }

    @Test
    fun missingTotalsIsMalformedNotZero() {
        assertNull(parseSummary(data("""{"viewer":{"accounts":[{}]}}""")))
        assertNull(parseSummary(data("""{"viewer":{"accounts":[{"totals":null}]}}""")))
    }

    @Test
    fun rowsAreAddedUp() {
        val parsed = parseSummary(account("""[{"sum":{"requests":10,"errors":1}},{"sum":{"requests":5,"errors":2}}]"""))

        assertEquals(SummaryData(15, 3), parsed)
    }

    @Test
    fun missingOrNonObjectSumIsMalformed() {
        assertNull(parseSummary(account("""[{"nothing":true}]""")))
        assertNull(parseSummary(account("""[{"sum":null}]""")))
        assertNull(parseSummary(account("""[{"sum":[1]}]""")))
    }

    @Test
    fun nonNumericOrNonIntegerOrMissingCountsAreMalformed() {
        assertNull(parseSummary(account("""[{"sum":{"requests":"many","errors":1}}]""")))
        assertNull(parseSummary(account("""[{"sum":{"requests":null,"errors":1}}]""")))
        assertNull(parseSummary(account("""[{"sum":{"requests":1.5,"errors":1}}]""")))
        assertNull(parseSummary(account("""[{"sum":{"requests":5}}]""")))
        assertNull(parseSummary(account("""[{"sum":{"requests":10,"errors":1}},{"sum":{"errors":1}}]""")))
    }

    @Test
    fun numericStringsAreAcceptedLikeHome() {
        assertEquals(SummaryData(7, 2), parseSummary(account("""[{"sum":{"requests":"7","errors":2}}]""")))
    }
}
