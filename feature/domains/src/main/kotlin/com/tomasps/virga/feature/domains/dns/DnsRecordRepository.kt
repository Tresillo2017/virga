package com.tomasps.virga.feature.domains.dns

import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.network.CloudflareApi
import com.tomasps.virga.core.network.FormPart
import com.tomasps.virga.core.network.cloudflarePath
import com.tomasps.virga.feature.domains.hasMorePages
import io.ktor.http.ContentType
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** One page of records and whether another page follows. */
data class DnsRecordPage(val records: List<DnsRecord>, val hasMore: Boolean)

/** Records are read a page at a time: the list screen loads more on request. */
class DnsRecordRepository(private val api: CloudflareApi) {

    suspend fun page(zoneId: String, page: Int, perPage: Int = RECORD_PAGE_SIZE, type: String? = null): ApiResult<DnsRecordPage> {
        val query = buildMap {
            put("page", page.toString())
            put("per_page", perPage.toString())
            if (type != null) put("type", type)
        }
        return when (val result = api.get<List<DnsRecord>>(recordsPath(zoneId), query)) {
            is ApiResult.Success -> ApiResult.Success(
                DnsRecordPage(
                    records = result.value,
                    hasMore = hasMorePages(result.pageInfo, page, result.value.size, perPage),
                ),
            )
            is ApiResult.Failure -> result
        }
    }

    suspend fun create(zoneId: String, body: JsonObject): ApiResult<DnsRecord> =
        api.post(recordsPath(zoneId), body)

    suspend fun update(zoneId: String, recordId: String, body: JsonObject): ApiResult<DnsRecord> =
        api.patch(cloudflarePath("zones", zoneId, "dns_records", recordId), body)

    /** Turns the proxy on or off with a partial PATCH. The reference recommends PATCH for toggles. */
    suspend fun setProxied(zoneId: String, recordId: String, proxied: Boolean): ApiResult<DnsRecord> = api.patch(
        cloudflarePath("zones", zoneId, "dns_records", recordId),
        buildJsonObject { put("proxied", proxied) },
    )

    suspend fun delete(zoneId: String, recordId: String): ApiResult<JsonElement?> =
        api.delete<JsonElement?>(cloudflarePath("zones", zoneId, "dns_records", recordId))

    /** One batch request with only deletes, which is what the bulk action needs. The batch endpoint has no PUT field. */
    suspend fun deleteBatch(zoneId: String, recordIds: List<String>): ApiResult<JsonElement?> = api.post(
        cloudflarePath("zones", zoneId, "dns_records", "batch"),
        buildJsonObject {
            put("deletes", JsonArray(recordIds.map { id -> buildJsonObject { put("id", JsonPrimitive(id)) } }))
        },
    )

    /** BIND zone file import. Multipart with the file in the `file` field. */
    suspend fun importBind(zoneId: String, fileName: String, bytes: ByteArray): ApiResult<JsonElement?> = api.postMultipart(
        cloudflarePath("zones", zoneId, "dns_records", "import"),
        listOf(FormPart.File(name = "file", fileName = fileName, contentType = ContentType.Text.Plain, bytes = bytes)),
        JsonElement.serializer().nullable,
    )

    /** BIND zone file export as plain text. */
    suspend fun exportBind(zoneId: String): ApiResult<String> =
        api.getText(cloudflarePath("zones", zoneId, "dns_records", "export"))

    private fun recordsPath(zoneId: String): String = cloudflarePath("zones", zoneId, "dns_records")

    companion object {
        const val RECORD_PAGE_SIZE = 100
    }
}
