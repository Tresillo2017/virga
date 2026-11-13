package com.tomasps.virga.feature.r2.api

import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.CloudflareMessage
import com.tomasps.virga.core.ui.state.UiText
import com.tomasps.virga.core.ui.R as CoreUiR
import com.tomasps.virga.feature.r2.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class R2ErrorUiMapperTest {

    @Test
    fun `401 with code 10002 explains that an Admin Read and Write token is needed`() {
        // Arrange
        val error = ApiError.Unauthorized(listOf(CloudflareMessage(code = 10002, message = "raw")))

        // Act
        val ui = R2ErrorUiMapper.toErrorUi(error)

        // Assert
        assertEquals(UiText.Resource(R.string.r2_error_admin_all_title), ui.title)
        assertEquals(UiText.Resource(R.string.r2_error_admin_all_message), ui.message)
        assertFalse(ui.retryable)
    }

    @Test
    fun `403 with code 10000 explains that the token is limited to a bucket`() {
        // Arrange
        val error = ApiError.Forbidden(listOf(CloudflareMessage(code = 10000)))

        // Act
        val ui = R2ErrorUiMapper.toErrorUi(error)

        // Assert
        assertEquals(UiText.Resource(R.string.r2_error_admin_bucket_title), ui.title)
        assertEquals(UiText.Resource(R.string.r2_error_admin_bucket_message), ui.message)
    }

    @Test
    fun `other 403 errors fall back to the shared mapping`() {
        // Arrange
        val error = ApiError.Forbidden(listOf(CloudflareMessage(code = 9999)))

        // Act
        val ui = R2ErrorUiMapper.toErrorUi(error)

        // Assert
        assertEquals(UiText.Resource(CoreUiR.string.core_ui_error_forbidden_title), ui.title)
    }

    @Test
    fun `a 401 without codes is treated as a generic unauthorized error`() {
        // Act
        val ui = R2ErrorUiMapper.toErrorUi(ApiError.Unauthorized())

        // Assert
        assertEquals(UiText.Resource(CoreUiR.string.core_ui_error_unauthorized_title), ui.title)
    }
}
