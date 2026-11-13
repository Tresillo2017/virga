package com.tomasps.virga.core.common.builds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildEnumsTest {

    @Test
    fun `status maps known wire values regardless of case`() {
        assertEquals(BuildStatus.RUNNING, BuildStatus.fromWire("running"))
        assertEquals(BuildStatus.QUEUED, BuildStatus.fromWire("QUEUED"))
        assertEquals(BuildStatus.INITIALIZING, BuildStatus.fromWire(" initializing "))
        assertEquals(BuildStatus.STOPPED, BuildStatus.fromWire("stopped"))
    }

    @Test
    fun `status falls back to unknown for null or a new value`() {
        assertEquals(BuildStatus.UNKNOWN, BuildStatus.fromWire(null))
        assertEquals(BuildStatus.UNKNOWN, BuildStatus.fromWire("paused-by-magic"))
    }

    @Test
    fun `only queued, initializing and running builds are active`() {
        assertTrue(BuildStatus.QUEUED.isActive)
        assertTrue(BuildStatus.INITIALIZING.isActive)
        assertTrue(BuildStatus.RUNNING.isActive)
        assertFalse(BuildStatus.STOPPED.isActive)
        assertFalse(BuildStatus.UNKNOWN.isActive)
    }

    @Test
    fun `outcome accepts both spellings of canceled`() {
        assertEquals(BuildOutcome.CANCELED, BuildOutcome.fromWire("canceled"))
        assertEquals(BuildOutcome.CANCELED, BuildOutcome.fromWire("Cancelled"))
        assertEquals(BuildOutcome.SUCCESS, BuildOutcome.fromWire("success"))
        assertEquals(BuildOutcome.FAIL, BuildOutcome.fromWire("fail"))
        assertEquals(BuildOutcome.SKIPPED, BuildOutcome.fromWire("skipped"))
    }

    @Test
    fun `outcome falls back to unknown for null or a new value`() {
        assertEquals(BuildOutcome.UNKNOWN, BuildOutcome.fromWire(null))
        assertEquals(BuildOutcome.UNKNOWN, BuildOutcome.fromWire("exploded"))
    }
}
