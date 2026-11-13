package com.tomasps.virga.feature.d1.ui

import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.feature.d1.data.D1Limits
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class D1StatementErrorTest {

    @Test
    fun `a timed-out write is not offered as a retry, because it may already have been applied`() {
        val error = ApiError.Local(D1Limits.REQUEST_LIMIT_REASON)

        val ui = D1ErrorMapper.toStatementErrorUi(error, mayWrite = true)

        assertFalse(ui.retryable)
        assertTrue(ui.title != D1ErrorMapper.limitTitle)
    }

    @Test
    fun `a timed-out read is still retryable`() {
        val error = ApiError.Local(D1Limits.REQUEST_LIMIT_REASON)

        assertTrue(D1ErrorMapper.toStatementErrorUi(error, mayWrite = false).retryable)
    }

    @Test
    fun `other failures of a write keep their normal wording`() {
        val error = ApiError.Local("something else")

        assertTrue(D1ErrorMapper.toStatementErrorUi(error, mayWrite = true) == D1ErrorMapper.toErrorUi(error))
    }
}
