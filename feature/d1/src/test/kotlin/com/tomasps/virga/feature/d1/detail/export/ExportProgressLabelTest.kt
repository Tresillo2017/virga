package com.tomasps.virga.feature.d1.detail.export

import com.tomasps.virga.feature.d1.R
import org.junit.Assert.assertEquals
import org.junit.Test

class ExportProgressLabelTest {

    @Test
    fun `a poll that has not reported a status reads as starting`() {
        assertEquals(R.string.d1_export_status_starting, exportProgressLabelRes(null))
    }

    @Test
    fun `any running status reads as in progress, never as the raw status id`() {
        assertEquals(R.string.d1_export_status_in_progress, exportProgressLabelRes("in_progress"))
        assertEquals(R.string.d1_export_status_in_progress, exportProgressLabelRes("some_new_status"))
    }
}
