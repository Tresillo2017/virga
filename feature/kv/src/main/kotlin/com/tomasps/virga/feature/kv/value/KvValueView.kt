package com.tomasps.virga.feature.kv.value

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** Bytes are shown as text only when they are valid UTF-8 with no NUL byte. Anything else is a binary preview. */
sealed interface KvValueView {
    /** [text] is what to display: pretty-printed when [isJson], otherwise the original text. */
    data class Text(val text: String, val isJson: Boolean) : KvValueView

    /** [hexPreview] covers the first [previewBytes] bytes; [truncated] is true when the value is longer. */
    data class Binary(
        val size: Long,
        val hexPreview: String,
        val previewBytes: Int,
        val truncated: Boolean,
    ) : KvValueView
}

const val KV_HEX_PREVIEW_BYTES = 256

private const val NUL: Char = '\u0000'
private const val HEX_RADIX = 16
private const val HEX_WIDTH = 2
private const val HEX_MASK = 0xFF
private val prettyJson = Json { prettyPrint = true }

/** Decides how a value is shown. Pure, so the screen and the tests use the same rule. */
fun classifyValue(bytes: ByteArray): KvValueView {
    val text = decodeUtf8OrNull(bytes)?.takeIf { !it.contains(NUL) } ?: return binaryView(bytes)
    val element = parseJsonOrNull(text.trim())
    return if (element != null) {
        KvValueView.Text(text = prettyJson.encodeToString(JsonElement.serializer(), element), isJson = true)
    } else {
        KvValueView.Text(text = text, isJson = false)
    }
}

private fun parseJsonOrNull(text: String): JsonElement? {
    if (!text.startsWith("{") && !text.startsWith("[")) return null
    return runCatching { Json.parseToJsonElement(text) }.getOrNull()
}

private fun binaryView(bytes: ByteArray): KvValueView.Binary {
    val previewBytes = minOf(bytes.size, KV_HEX_PREVIEW_BYTES)
    val hex = (0 until previewBytes).joinToString(" ") { index ->
        (bytes[index].toInt() and HEX_MASK).toString(HEX_RADIX).padStart(HEX_WIDTH, '0')
    }
    return KvValueView.Binary(
        size = bytes.size.toLong(),
        hexPreview = hex,
        previewBytes = previewBytes,
        truncated = bytes.size > KV_HEX_PREVIEW_BYTES,
    )
}

/** Strict UTF-8 decoding: returns null for any byte sequence that is not valid UTF-8. */
internal fun decodeUtf8OrNull(bytes: ByteArray): String? = runCatching {
    Charsets.UTF_8.newDecoder()
        .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
        .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
        .decode(java.nio.ByteBuffer.wrap(bytes))
        .toString()
}.getOrNull()
