package com.tomasps.virga.core.network

import io.ktor.http.ContentType

/** One part of a multipart/form-data upload. Files are buffered; use [CloudflareApi.uploadStream] for large bodies. */
sealed interface FormPart {
    val name: String

    /** [contentType] is sent as the part's Content-Type header when set, for example `application/json` metadata. */
    data class Text(override val name: String, val value: String, val contentType: ContentType? = null) : FormPart

    data class File(
        override val name: String,
        val fileName: String,
        val contentType: ContentType,
        val bytes: ByteArray,
    ) : FormPart {
        override fun equals(other: Any?): Boolean = other is File && other.name == name && other.bytes.contentEquals(bytes)
        override fun hashCode(): Int = name.hashCode() * 31 + bytes.contentHashCode()
    }
}
