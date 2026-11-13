package com.tomasps.virga.feature.kv.value

import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.ui.state.UiText
import com.tomasps.virga.feature.kv.R
import com.tomasps.virga.feature.kv.kvActionMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class KvErrorTextTest {

    @Test
    fun `a 429 on a single key write names the one-write-per-second limit`() {
        // Arrange
        val error = ApiError.RateLimited(retryAfterMillis = 1_000L)

        // Act
        val message = kvActionMessage(error, isSingleKeyWrite = true)

        // Assert
        assertEquals(R.string.kv_error_key_write_rate_limited, (message as UiText.Resource).id)
    }

    @Test
    fun `a 429 on a namespace or bulk action does not blame the per-key limit`() {
        // Arrange
        val error = ApiError.RateLimited(retryAfterMillis = 1_000L)

        // Act
        val message = kvActionMessage(error, isSingleKeyWrite = false)

        // Assert
        assertNotEquals(R.string.kv_error_key_write_rate_limited, (message as? UiText.Resource)?.id)
    }

    @Test
    fun `a missing key maps to the not-found text`() {
        // Act
        val message = kvActionMessage(ApiError.NotFound(), isSingleKeyWrite = false)

        // Assert
        assertEquals(R.string.kv_error_not_found, (message as UiText.Resource).id)
    }
}
