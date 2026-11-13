package com.tomasps.virga.core.network

import com.tomasps.virga.core.network.demo.*
import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.common.result.PageInfo
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvelopeDecodingTest {
    @Serializable
    data class Worker(val id: String, val name: String? = null)

    private fun <T> decode(status: Int, body: String, serializer: kotlinx.serialization.KSerializer<T>): ApiResult<T> =
        decodeEnvelope(CloudflareJson, status, body, retryAfterMillis = null, deserializer = serializer)

    @Test
    fun `decodes an object result and ignores unknown keys`() {
        val body = """{"success":true,"errors":[],"messages":[],"result":{"id":"w1","extra":5}}"""
        val result = decode(200, body, Worker.serializer())
        assertEquals(Worker("w1"), (result as ApiResult.Success).value)
    }

    @Test
    fun `decodes an array result`() {
        val body = """{"success":true,"errors":[],"messages":[],"result":[{"id":"a"},{"id":"b","name":"B"}]}"""
        val result = decode(200, body, ListSerializer(Worker.serializer()))
        assertEquals(listOf(Worker("a"), Worker("b", "B")), (result as ApiResult.Success).value)
    }

    @Test
    fun `null result decodes into a nullable type`() {
        val body = """{"success":true,"errors":[],"messages":[],"result":null}"""
        val result = decode(200, body, Worker.serializer().nullable)
        assertNull((result as ApiResult.Success).value)
    }

    @Test
    fun `null or absent result decodes into Unit`() {
        val nullResult = decode(200, """{"success":true,"result":null}""", kotlin.Unit.serializer())
        val absentResult = decode(200, """{"success":true}""", kotlin.Unit.serializer())
        assertTrue(nullResult is ApiResult.Success)
        assertTrue(absentResult is ApiResult.Success)
    }

    @Test
    fun `absent result for a non-null type is a decode error`() {
        val result = decode(200, """{"success":true}""", Worker.serializer())
        assertTrue((result as ApiResult.Failure).error is ApiError.Decode)
    }

    @Test
    fun `page style result_info is parsed`() {
        val body = """{"success":true,"result":[],"result_info":{"page":2,"per_page":20,"count":20,"total_count":45}}"""
        val info = (decode(200, body, ListSerializer(Worker.serializer())) as ApiResult.Success).pageInfo
        assertEquals(PageInfo.Page(page = 2, perPage = 20, count = 20, totalCount = 45), info)
    }

    @Test
    fun `cursor style result_info is parsed with the next cursor`() {
        val body = """{"success":true,"result":[],"result_info":{"per_page":100,"count":100,"cursors":{"after":"abc"}}}"""
        val info = (decode(200, body, ListSerializer(Worker.serializer())) as ApiResult.Success).pageInfo
        assertEquals(PageInfo.Cursor(nextCursor = "abc", perPage = 100, count = 100), info)
    }

    @Test
    fun `a blank next cursor is kept as null`() {
        val body = """{"success":true,"result":[],"result_info":{"cursors":{"after":""}}}"""
        val info = (decode(200, body, ListSerializer(Worker.serializer())) as ApiResult.Success).pageInfo
        assertEquals(PageInfo.Cursor(nextCursor = null, perPage = null, count = null), info)
    }

    @Test
    fun `success false on a 2xx status is a rejection carrying the messages`() {
        val body = """{"success":false,"errors":[{"code":7003,"message":"No route"}],"result":null}"""
        val error = (decode(200, body, Worker.serializer().nullable) as ApiResult.Failure).error
        assertTrue(error is ApiError.Rejected)
        assertEquals(listOf(7003), error.codes)
    }

    @Test
    fun `a 2xx body that is not JSON is a decode error`() {
        val error = (decode(200, "<html>", Worker.serializer()) as ApiResult.Failure).error
        assertTrue(error is ApiError.Decode)
    }

    @Test
    fun `a non-JSON error body still maps by status`() {
        val error = (decode(502, "<html>bad gateway</html>", Worker.serializer()) as ApiResult.Failure).error
        assertTrue(error is ApiError.Server)
    }

    @Test
    fun `string error codes are accepted`() {
        val body = """{"success":false,"errors":[{"code":"10000","message":"Authentication error"}]}"""
        val error = (decode(403, body, Worker.serializer()) as ApiResult.Failure).error
        assertEquals(listOf(10000), error.codes)
    }
}
