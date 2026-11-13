package com.tomasps.virga.feature.r2.demo

private val DEMO_CONTENT_TYPES = mapOf(
    "html" to "text/html",
    "css" to "text/css",
    "js" to "text/javascript",
    "json" to "application/json",
    "txt" to "text/plain",
    "log" to "text/plain",
    "png" to "image/png",
    "jpg" to "image/jpeg",
    "jpeg" to "image/jpeg",
    "gif" to "image/gif",
    "svg" to "image/svg+xml",
    "pdf" to "application/pdf",
    "tar" to "application/x-tar",
    "zip" to "application/zip",
)

/** The content type a demo upload is recorded with, guessed from the key's extension; null when none is known. */
fun demoContentTypeFor(objectKey: String): String? {
    val extension = objectKey.substringAfterLast('.', missingDelimiterValue = "").lowercase()
    return DEMO_CONTENT_TYPES[extension]
}
