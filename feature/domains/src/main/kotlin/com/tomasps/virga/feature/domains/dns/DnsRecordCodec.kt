package com.tomasps.virga.feature.domains.dns

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

private const val CONTENT_KEY = "content"
private const val PRIORITY_KEY = "priority"

/** Tags are a paid-plan feature, and the reference caps them at 20 per record. */
const val MAX_TAGS_PER_RECORD = 20

/**
 * The JSON body for create (POST) and update (PATCH) of a record. Per type: proxied only where proxiable, content for
 * simple types, top-level priority for MX, and a data object for the structured types. Proxied records are sent with
 * Auto TTL. Tags are sent only on paid plans.
 */
fun DnsRecordForm.toRequestBody(isPaidPlan: Boolean): JsonObject {
    val type = recordType
    val effectiveTtl = if (ttlIsForcedAuto) AUTO_TTL else ttl.trim().toIntOrNull() ?: AUTO_TTL
    return buildJsonObject {
        put("type", type?.name ?: this@toRequestBody.type)
        put("name", name.trim())
        put("ttl", effectiveTtl)
        put("comment", comment.trim())
        if (type?.proxiable == true) put("proxied", proxied)
        if (isPaidPlan) put("tags", JsonArray(parseTags(tags).map { JsonPrimitive(it) }))
        putValue(type, values)
    }
}

private fun kotlinx.serialization.json.JsonObjectBuilder.putValue(type: DnsRecordType?, values: Map<String, String>) {
    when (type?.kind) {
        DnsValueKind.CONTENT, DnsValueKind.GENERIC, null -> put(CONTENT_KEY, values[CONTENT_KEY].orEmpty().trim())
        DnsValueKind.CONTENT_WITH_PRIORITY -> {
            put(CONTENT_KEY, values[CONTENT_KEY].orEmpty().trim())
            put(PRIORITY_KEY, values[PRIORITY_KEY].orEmpty().trim().toIntOrNull() ?: 0)
        }
        DnsValueKind.STRUCTURED -> put("data", structuredData(type, values))
    }
}

private fun structuredData(type: DnsRecordType, values: Map<String, String>): JsonObject = buildJsonObject {
    type.dataFields.forEach { field ->
        val raw = values[field].orEmpty().trim()
        val number = raw.toIntOrNull()
        if (number != null) put(field, number) else put(field, raw)
    }
}

/** Trimmed, non-empty tags from the comma-separated input. */
fun parseTags(raw: String): List<String> = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }

/** The form a record opens into. Unknown record types keep their content and cannot change type. */
fun DnsRecord.toForm(): DnsRecordForm {
    val known = DnsRecordType.fromWire(type)
    val values: Map<String, String> = when (known?.kind) {
        DnsValueKind.STRUCTURED -> known.dataFields.associateWith { field ->
            data?.get(field)?.let { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
        }
        DnsValueKind.CONTENT_WITH_PRIORITY -> mapOf(
            CONTENT_KEY to content.orEmpty(),
            PRIORITY_KEY to (priority ?: data?.get(PRIORITY_KEY)?.let { (it as? JsonPrimitive)?.intOrNull } ?: 0).toString(),
        )
        else -> mapOf(CONTENT_KEY to content.orEmpty())
    }
    return DnsRecordForm(
        type = type,
        name = name,
        ttl = ttl.toString(),
        proxied = proxied,
        comment = comment.orEmpty(),
        tags = tags.joinToString(", "),
        values = values,
    )
}

/**
 * The value a list row shows: the plain content when there is one, otherwise the structured fields in the order the
 * form uses (SRV reads `10 5 5060 sip.example.com`, CAA reads `0 issue "ca.example"`). Never raw JSON.
 */
fun DnsRecord.displayValue(): String {
    content?.takeIf { it.isNotBlank() }?.let { return it }
    val fields = data ?: return ""
    return structuredValueText(DnsRecordType.fromWire(type), fields)
}

/**
 * The structured `data` of a record joined as one zone-file value, in the form's field order. CAA quotes its value.
 * Shared by the list row and the BIND export so both show the same text.
 */
fun structuredValueText(type: DnsRecordType?, fields: JsonObject): String {
    val keys = type?.dataFields?.takeIf { it.isNotEmpty() } ?: fields.keys.toList()
    return keys.mapNotNull { key ->
        val text = (fields[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        if (key == F_VALUE && type == DnsRecordType.CAA) "\"$text\"" else text
    }.joinToString(" ")
}
