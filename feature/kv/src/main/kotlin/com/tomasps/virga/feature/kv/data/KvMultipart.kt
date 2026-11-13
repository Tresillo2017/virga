package com.tomasps.virga.feature.kv.data

import io.ktor.http.ContentType
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

/** Boundary used for every single-value PUT. Fixed so the request length can be computed up front. */
const val KV_MULTIPART_BOUNDARY = "virga-kv-7f3a9c2e"

private const val CRLF = "\r\n"
private const val VALUE_NAME = "value"
private const val METADATA_NAME = "metadata"

/**
 * A multipart/form-data body for a single-value PUT: a `value` part, and a `metadata` part when metadata is set.
 * The value is never copied into the framing, so a 25 MiB value is written straight from its buffer.
 */
class KvMultipartBody(
    val prefix: ByteArray,
    val value: ByteArray,
    val suffix: ByteArray,
) {
    val length: Long get() = (prefix.size + value.size + suffix.size).toLong()

    companion object {
        val contentType: ContentType = ContentType.MultiPart.FormData.withParameter("boundary", KV_MULTIPART_BOUNDARY)

        /** Builds the body. A null or JSON-null [metadata] leaves the metadata part out. */
        fun of(value: ByteArray, metadata: JsonElement?): KvMultipartBody {
            val boundary = "--$KV_MULTIPART_BOUNDARY"
            val prefix = buildString {
                append(boundary).append(CRLF)
                append("Content-Disposition: form-data; name=\"$VALUE_NAME\"").append(CRLF)
                append("Content-Type: application/octet-stream").append(CRLF).append(CRLF)
            }
            val metadataPart = metadata?.takeUnless { it == JsonNull }?.let { element ->
                buildString {
                    append(CRLF).append(boundary).append(CRLF)
                    append("Content-Disposition: form-data; name=\"$METADATA_NAME\"").append(CRLF)
                    append("Content-Type: application/json").append(CRLF).append(CRLF)
                    append(element.toString())
                }
            }.orEmpty()
            val suffix = "$CRLF$boundary--$CRLF"
            return KvMultipartBody(
                prefix = prefix.encodeToByteArray(),
                value = value,
                suffix = (metadataPart + suffix).encodeToByteArray(),
            )
        }
    }
}
