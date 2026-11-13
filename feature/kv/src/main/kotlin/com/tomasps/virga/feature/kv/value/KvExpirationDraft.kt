package com.tomasps.virga.feature.kv.value

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

enum class KvExpirationMode { NONE, ABSOLUTE, TTL }

/** What the user typed in the expiration editor. [input] is "yyyy-MM-dd HH:mm" for ABSOLUTE and seconds for TTL. */
data class KvExpirationDraft(val mode: KvExpirationMode, val input: String)

enum class ExpirationInputProblem { NOT_A_NUMBER, NOT_A_DATE_TIME }

sealed interface ExpirationParse {
    data class Ok(val expiration: KvExpiration) : ExpirationParse

    data class Invalid(val problem: ExpirationInputProblem) : ExpirationParse
}

private const val ABSOLUTE_PATTERN = "yyyy-MM-dd HH:mm"

/** Turns the editor's draft into a [KvExpiration]. The 60 second rule is checked separately, against the clock. */
fun parseExpirationDraft(draft: KvExpirationDraft, zone: ZoneId): ExpirationParse = when (draft.mode) {
    KvExpirationMode.NONE -> ExpirationParse.Ok(KvExpiration.None)
    KvExpirationMode.TTL -> draft.input.trim().toLongOrNull()
        ?.let { ExpirationParse.Ok(KvExpiration.Ttl(it)) }
        ?: ExpirationParse.Invalid(ExpirationInputProblem.NOT_A_NUMBER)
    KvExpirationMode.ABSOLUTE -> parseAbsolute(draft.input.trim(), zone)
}

private fun parseAbsolute(text: String, zone: ZoneId): ExpirationParse = try {
    val local = LocalDateTime.parse(text, DateTimeFormatter.ofPattern(ABSOLUTE_PATTERN))
    ExpirationParse.Ok(KvExpiration.Absolute(local.atZone(zone).toEpochSecond()))
} catch (error: DateTimeParseException) {
    ExpirationParse.Invalid(ExpirationInputProblem.NOT_A_DATE_TIME)
}
