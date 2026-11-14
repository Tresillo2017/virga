package com.tomasps.virga.feature.builds.api

import com.tomasps.virga.core.common.builds.BuildOutcome
import com.tomasps.virga.core.common.builds.BuildStatus
import com.tomasps.virga.feature.builds.testing.record
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

private fun json(text: String): JsonElement = Json.parseToJsonElement(text)
private val CREATED: Instant = Instant.parse("2026-11-11T10:00:00Z")

class BuildParsingTest {

    @Test
    fun `log lines are read from timestamp and message pairs and the message is the string`() {
        val page = parseLogPage(json("""{"lines":[[1700000000,"Cloning"],[1700000001,"Build ok"]],"cursor":"c9"}"""))

        assertEquals(listOf("Cloning", "Build ok"), page.lines)
        assertEquals("c9", page.cursor)
        assertEquals(false, page.truncated)
    }

    @Test
    fun `a log without lines or cursor reads as an empty page`() {
        val page = parseLogPage(json("""{"truncated":true}"""))

        assertTrue(page.lines.isEmpty())
        assertNull(page.cursor)
        assertTrue(page.truncated)
    }

    @Test
    fun `a log line that is only a number is not shown as text`() {
        val page = parseLogPage(json("""{"lines":[[1700000000,1700000000]],"cursor":null}"""))

        assertTrue(page.lines.isEmpty())
    }

    @Test
    fun `a log reply that is not an object reads as no lines`() {
        assertTrue(parseLogPage(json("[1,2,3]")).lines.isEmpty())
        assertTrue(parseLogPage(null).lines.isEmpty())
    }

    @Test
    fun `trigger parsing accepts an array or an object and skips items without a uuid`() {
        val fromArray = parseTriggers(json("""[{"trigger_uuid":"t-1","trigger_name":"main"},{"trigger_name":"no id"}]"""))
        val fromObject = parseTriggers(json("""{"triggers":[{"trigger_uuid":"t-2","branch_includes":["main"]}]}"""))

        assertEquals(listOf("t-1"), fromArray.map { it.triggerUuid })
        assertEquals(listOf("t-2"), fromObject.map { it.triggerUuid })
        assertEquals(listOf("main"), fromObject.single().branchIncludes)
    }

    @Test
    fun `the started build uuid is read only when present and not blank`() {
        assertEquals("b-1", parseStartedBuildUuid(json("""{"build_uuid":"b-1"}""")))
        assertNull(parseStartedBuildUuid(json("""{"build_uuid":"  "}""")))
        assertNull(parseStartedBuildUuid(json("[]")))
    }

    @Test
    fun `both spellings of a failed outcome map to fail`() {
        assertEquals("fail", normalizeOutcome("failure"))
        assertEquals("fail", normalizeOutcome("FAILED"))
        assertEquals("success", normalizeOutcome("success"))
        assertEquals(BuildOutcome.FAIL, BuildOutcome.fromWire(normalizeOutcome("failed")))
    }

    @Test
    fun `cancelled, canceled and terminated outcomes never crash the parse`() {
        assertEquals(BuildOutcome.CANCELED, BuildOutcome.fromWire("cancelled"))
        assertEquals(BuildOutcome.CANCELED, BuildOutcome.fromWire("canceled"))
        assertEquals(BuildOutcome.UNKNOWN, BuildOutcome.fromWire("terminated"))
    }

    @Test
    fun `a DTO with a bad timestamp keeps the build out of the list rather than crashing`() {
        val dto = BuildDto(buildUuid = "b-1", createdOn = "not a time")

        assertNull(dto.toRecord("api"))
    }

    @Test
    fun `a DTO without a uuid is dropped and status strings parse case-insensitively`() {
        val dto = BuildDto(buildUuid = null, createdOn = "2026-11-11T10:00:00Z")
        val valid = BuildDto(buildUuid = "b-2", status = "RUNNING", createdOn = "2026-11-11T10:00:00Z")

        assertNull(dto.toRecord("api"))
        assertEquals(BuildStatus.RUNNING, valid.toRecord("api")?.status)
    }

    @Test
    fun `a build is terminal once it stops or once it has a known outcome`() {
        assertTrue(record(status = BuildStatus.STOPPED).isTerminal)
        assertTrue(record(status = BuildStatus.QUEUED, outcome = BuildOutcome.SKIPPED).isTerminal)
        assertTrue(!record(status = BuildStatus.RUNNING).isTerminal)
    }

    @Test
    fun `stage timings count a running stage up to now and a finished build stops at its end`() {
        val running = record(status = BuildStatus.RUNNING, created = CREATED)
        val now = CREATED.plusSeconds(10)
        val timings = stageTimings(running, now).associateBy { it.stage }

        assertEquals(Duration.ofSeconds(1), timings.getValue(BuildStage.QUEUED).elapsed)
        assertEquals(Duration.ofSeconds(1), timings.getValue(BuildStage.INITIALIZING).elapsed)
        assertEquals(Duration.ofSeconds(8), timings.getValue(BuildStage.RUNNING).elapsed)
        assertNull(timings.getValue(BuildStage.DONE).elapsed)
    }

    @Test
    fun `a negative span from clock skew is clamped to zero`() {
        val skewed = record(status = BuildStatus.RUNNING, created = CREATED).copy(
            initializingOn = CREATED.plusSeconds(5),
            runningOn = CREATED.plusSeconds(2),
        )

        val initializing = stageTimings(skewed, CREATED.plusSeconds(20)).single { it.stage == BuildStage.INITIALIZING }

        assertEquals(Duration.ZERO, initializing.elapsed)
    }

    @Test
    fun `a finished build reports its total in the done stage`() {
        val finished = record(status = BuildStatus.STOPPED, outcome = BuildOutcome.SUCCESS, created = CREATED)

        val done = stageTimings(finished, CREATED.plusSeconds(999)).single { it.stage == BuildStage.DONE }

        assertEquals(Duration.ofSeconds(30), done.elapsed)
    }
}
