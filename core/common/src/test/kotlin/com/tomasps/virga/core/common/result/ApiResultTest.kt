package com.tomasps.virga.core.common.result

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiResultTest {

    private val page = PageInfo.Page(page = 2, perPage = 25, count = 25, totalCount = 80)

    @Test
    fun `map transforms a success and keeps its page info`() {
        val mapped = ApiResult.Success(3, page).map { it * 2 }

        assertEquals(ApiResult.Success(6, page), mapped)
    }

    @Test
    fun `map passes a failure through untouched and never calls the transform`() {
        val failure: ApiResult<Int> = ApiResult.Failure(ApiError.NotFound())
        var calls = 0

        val mapped = failure.map { calls++; it * 2 }

        assertEquals(failure, mapped)
        assertEquals(0, calls)
    }

    @Test
    fun `getOrNull and errorOrNull each answer only for their own branch`() {
        val ok: ApiResult<String> = ApiResult.Success("value")
        val failed: ApiResult<String> = ApiResult.Failure(ApiError.Network("offline"))

        assertEquals("value", ok.getOrNull())
        assertNull(ok.errorOrNull())
        assertNull(failed.getOrNull())
        assertEquals(ApiError.Network("offline"), failed.errorOrNull())
    }
}
