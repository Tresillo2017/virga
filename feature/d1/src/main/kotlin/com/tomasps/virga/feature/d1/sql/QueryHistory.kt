package com.tomasps.virga.feature.d1.sql

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Pure rules for the console's recent-query list. Newest first, no duplicates, capped. */
object QueryHistory {
    const val MAX_ENTRIES = 25

    /** Longest one-line title shown for an entry, ellipsis included. */
    const val HEADLINE_CHARS = 60

    private const val ELLIPSIS = "…"

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(String.serializer())
    private val whitespace = Regex("\\s+")

    /** The statement is stored exactly as it ran, trimmed. Re-running a statement moves it to the top. */
    fun add(history: List<String>, sql: String): List<String> {
        val text = sql.trim()
        if (text.isEmpty()) return history
        return (listOf(text) + history.filter { it != text }).take(MAX_ENTRIES)
    }

    /** One line for the list: whitespace runs (line breaks included) become single spaces, and long text ends in an ellipsis. */
    fun headline(entry: String, maxChars: Int = HEADLINE_CHARS): String {
        val oneLine = entry.trim().replace(whitespace, " ")
        if (oneLine.length <= maxChars) return oneLine
        return oneLine.take(maxChars - ELLIPSIS.length).trimEnd() + ELLIPSIS
    }

    fun encode(history: List<String>): String = json.encodeToString(serializer, history)

    /**
     * Missing or unreadable stored data is an empty history, never an error the user sees. Stored entries are kept
     * in order, but a repeated or blank entry is dropped, so every entry is a distinct list key.
     */
    fun decode(stored: String?): List<String> {
        if (stored.isNullOrBlank()) return emptyList()
        val entries = runCatching { json.decodeFromString(serializer, stored) }.getOrDefault(emptyList())
        return entries.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(MAX_ENTRIES)
    }
}
