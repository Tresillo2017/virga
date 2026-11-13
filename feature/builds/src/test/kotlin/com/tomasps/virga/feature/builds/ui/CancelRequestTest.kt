package com.tomasps.virga.feature.builds.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CancelRequestTest {

    @Test
    fun `a cancel flag for this build opens the confirmation`() {
        assertTrue(isCancelRequestFor(extra = "build-1", buildUuid = "build-1"))
    }

    @Test
    fun `a flag for another build, or none, does nothing`() {
        assertFalse(isCancelRequestFor(extra = "build-2", buildUuid = "build-1"))
        assertFalse(isCancelRequestFor(extra = null, buildUuid = "build-1"))
        assertFalse(isCancelRequestFor(extra = "", buildUuid = ""))
    }
}
