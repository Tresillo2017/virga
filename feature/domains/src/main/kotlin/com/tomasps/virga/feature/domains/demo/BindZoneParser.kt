package com.tomasps.virga.feature.domains.demo

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val MULTIPART_FILE_FIELD = "name=\"file\""
private const val PART_HEADERS_END = "\r\n\r\n"
private const val PART_CLOSE = "\r\n--"
private const val DEFAULT_DEMO_TTL = 1
private const val CLASS_IN = "IN"
private const val TYPE_MX = "MX"
private const val TYPE_SOA = "SOA"
private const val TYPE_TXT = "TXT"
private const val DIRECTIVE_ORIGIN = "\$ORIGIN"
private const val DIRECTIVE_TTL = "\$TTL"
private const val APEX = "@"
private const val QUOTE = '"'
private const val COMMENT = ';'
private const val OPEN_PAREN = '('
private const val CLOSE_PAREN = ')'
private val BIND_CLASSES = setOf("IN", "CH", "HS", "CS")

/** One record read from an uploaded BIND zone file, before it gets an id and zone. */
internal data class BindRecord(val name: String, val type: String, val content: String, val ttl: Int, val priority: Int?)

/**
 * Returns the text of the `file` part when [body] is a multipart upload, and [body] unchanged otherwise. The real
 * client sends the zone file as a multipart part named `file`, so the demo reads the same bytes.
 */
internal fun uploadedFileText(body: String): String {
    val fieldAt = body.indexOf(MULTIPART_FILE_FIELD)
    if (fieldAt < 0) return body
    val contentStart = body.indexOf(PART_HEADERS_END, fieldAt).takeIf { it >= 0 }?.plus(PART_HEADERS_END.length)
        ?: return body
    val contentEnd = body.indexOf(PART_CLOSE, contentStart).takeIf { it >= 0 } ?: body.length
    return body.substring(contentStart, contentEnd)
}

/**
 * Parses a BIND master file as BIND writes one. It reads the `$ORIGIN` and `$TTL` directives, a record whose owner is
 * left out (an indented line repeats the owner above it), a record whose parentheses span several lines (the SOA),
 * quoted strings, and `;` comments outside quotes. The SOA is skipped, because the API does not store one. A line that
 * is not a record is skipped too, so one bad line never fails the whole import.
 */
internal fun parseBindZone(text: String): List<BindRecord> {
    val parser = BindParser()
    bindLines(text).forEach(parser::accept)
    return parser.records
}

/** One logical line of a zone file: the tokens between comments and parentheses, and whether it was indented. */
internal data class BindLine(val indented: Boolean, val tokens: List<String>)

/** Joins the physical lines of a zone file into logical lines: a line inside parentheses continues the one before. */
internal fun bindLines(text: String): List<BindLine> {
    val lines = mutableListOf<BindLine>()
    val current = mutableListOf<String>()
    var currentIndented = false
    var depth = 0
    text.lineSequence().forEach { raw ->
        if (depth == 0) currentIndented = raw.startsWith(' ') || raw.startsWith('\t')
        val scanned = scanBindLine(raw)
        current += scanned.tokens
        depth = (depth + scanned.opens - scanned.closes).coerceAtLeast(0)
        if (depth == 0 && current.isNotEmpty()) {
            lines += BindLine(currentIndented, current.toList())
            current.clear()
        }
    }
    if (current.isNotEmpty()) lines += BindLine(currentIndented, current.toList())
    return lines
}

/** Tokens of one physical line, and how many parentheses it opens and closes. */
private class ScannedLine(val tokens: List<String>, val opens: Int, val closes: Int)

/** Splits a line on whitespace and parentheses, keeps quoted text together, and stops at a comment outside quotes. */
private fun scanBindLine(raw: String): ScannedLine {
    val tokens = mutableListOf<String>()
    val token = StringBuilder()
    var inQuote = false
    var opens = 0
    var closes = 0
    fun flush() {
        if (token.isNotEmpty()) {
            tokens += token.toString()
            token.clear()
        }
    }
    for (c in raw) {
        when {
            c == QUOTE -> {
                inQuote = !inQuote
                token.append(c)
            }
            inQuote -> token.append(c)
            c == COMMENT -> break
            c == OPEN_PAREN -> {
                flush()
                opens++
            }
            c == CLOSE_PAREN -> {
                flush()
                closes++
            }
            c.isWhitespace() -> flush()
            else -> token.append(c)
        }
    }
    flush()
    return ScannedLine(tokens, opens, closes)
}

/** Turns logical lines into records. Directives change the origin and the default TTL for the lines after them. */
private class BindParser {
    private var origin: String? = null
    private var defaultTtl = DEFAULT_DEMO_TTL
    private var lastOwner: String? = null
    val records = mutableListOf<BindRecord>()

    fun accept(line: BindLine) {
        when (line.tokens.first().uppercase()) {
            DIRECTIVE_ORIGIN -> origin = line.tokens.getOrNull(1)?.trimEnd('.')
            DIRECTIVE_TTL -> defaultTtl = line.tokens.getOrNull(1)?.toIntOrNull() ?: defaultTtl
            else -> parseRecord(line)
        }
    }

    private fun parseRecord(line: BindLine) {
        val fields = line.tokens.toMutableList()
        val owner = if (line.indented) lastOwner else fields.removeAt(0)
        if (owner == null) return
        lastOwner = owner
        var ttl = defaultTtl
        while (fields.isNotEmpty() && isTtlOrClass(fields.first())) {
            val token = fields.removeAt(0)
            if (token.all(Char::isDigit)) ttl = token.toIntOrNull() ?: ttl
        }
        if (fields.isEmpty()) return
        val type = fields.removeAt(0).uppercase()
        if (type == TYPE_SOA || fields.isEmpty()) return
        val priority = if (type == TYPE_MX) fields.firstOrNull()?.toIntOrNull()?.also { fields.removeAt(0) } else null
        records += BindRecord(
            name = qualifiedOwner(owner),
            type = type,
            content = fields.joinToString(" "),
            ttl = ttl,
            priority = priority,
        )
    }

    /** A name without a trailing dot is relative to the origin; `@` is the origin itself. */
    private fun qualifiedOwner(owner: String): String {
        val zone = origin ?: return owner
        return when {
            owner == APEX -> zone
            owner.endsWith('.') -> owner
            else -> "$owner.$zone"
        }
    }

    private fun isTtlOrClass(token: String): Boolean =
        token.all(Char::isDigit) || token.uppercase() in BIND_CLASSES
}

/**
 * The JSON the demo stores for a parsed record. The id and zone are added by the caller. The stored form is the API's:
 * a TXT value has no surrounding quotes, and a host name has no trailing dot.
 */
internal fun BindRecord.toDemoJson(): JsonObject = buildJsonObject {
    put("type", type)
    put("name", name)
    put("content", demoContent())
    put("ttl", ttl)
    priority?.let { put("priority", it) }
}

private fun BindRecord.demoContent(): String = when (type) {
    TYPE_TXT -> content.removeSurrounding("\"")
    else -> content.removeSuffix(".")
}
