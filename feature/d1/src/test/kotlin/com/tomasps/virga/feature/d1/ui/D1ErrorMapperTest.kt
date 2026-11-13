package com.tomasps.virga.feature.d1.ui

import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.CloudflareMessage
import com.tomasps.virga.core.ui.state.ApiErrorUiMapper
import com.tomasps.virga.core.ui.state.UiText
import com.tomasps.virga.feature.d1.data.D1Limits
import com.tomasps.virga.feature.d1.data.ExportRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class D1ErrorMapperTest {

    @Test
    fun `the 30 second limit gets its own retryable message`() {
        val ui = D1ErrorMapper.toErrorUi(ApiError.Local(D1Limits.REQUEST_LIMIT_REASON))

        assertEquals(D1ErrorMapper.limitTitle, ui.title)
        assertTrue(ui.retryable)
    }

    @Test
    fun `an export that ran out of time gets its own message`() {
        val ui = D1ErrorMapper.toErrorUi(ApiError.Local(ExportRepository.TIMED_OUT_REASON))

        assertEquals(D1ErrorMapper.exportTimeoutTitle, ui.title)
    }

    @Test
    fun `a result over 50 MB gets the size message, not a generic one`() {
        val error = ApiError.Rejected(
            status = 400,
            messages = listOf(CloudflareMessage(message = "D1 query result exceeded the maximum response size limit of 50 MB")),
        )

        assertEquals(D1ErrorMapper.resultTooLargeTitle, D1ErrorMapper.toErrorUi(error).title)
    }

    @Test
    fun `other errors keep the shared mapping`() {
        val error = ApiError.Forbidden()

        assertEquals(ApiErrorUiMapper.toErrorUi(error), D1ErrorMapper.toErrorUi(error))
    }

    @Test
    fun `the special titles are string resources, never raw API text`() {
        assertTrue(D1ErrorMapper.limitTitle is UiText.Resource)
        assertTrue(D1ErrorMapper.resultTooLargeTitle is UiText.Resource)
    }
}
