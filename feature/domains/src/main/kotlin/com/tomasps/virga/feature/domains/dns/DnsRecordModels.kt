package com.tomasps.virga.feature.domains.dns

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** One DNS record as the records endpoints return it. `data` is set for structured types (SRV, CAA, HTTPS, SVCB). */
@Serializable
data class DnsRecord(
    val id: String,
    val name: String = "",
    val type: String = "",
    val content: String? = null,
    val proxied: Boolean = false,
    val proxiable: Boolean = false,
    val ttl: Int = AUTO_TTL,
    val comment: String? = null,
    val tags: List<String> = emptyList(),
    val priority: Int? = null,
    val data: JsonObject? = null,
)

/** TTL value the API uses for "Automatic". */
const val AUTO_TTL = 1

/** How a record type's value is entered and sent. */
enum class DnsValueKind {
    /** One `content` string (A, AAAA, CNAME, TXT, NS, PTR). */
    CONTENT,

    /** `content` plus a top-level `priority` (MX). */
    CONTENT_WITH_PRIORITY,

    /** A `data` object whose fields are listed in [DnsRecordType.dataFields] (SRV, CAA, HTTPS, SVCB). */
    STRUCTURED,

    /** A record type Virga has no form for. The content is shown and sent as-is; the type cannot be changed. */
    GENERIC,
}

/**
 * Record types with a dedicated form. Proxiable types are the only ones the reference allows behind the orange
 * cloud. [dataFields] names the `data` keys in the order the form shows them.
 */
enum class DnsRecordType(
    val proxiable: Boolean,
    val kind: DnsValueKind,
    val dataFields: List<String> = emptyList(),
) {
    A(proxiable = true, kind = DnsValueKind.CONTENT),
    AAAA(proxiable = true, kind = DnsValueKind.CONTENT),
    CNAME(proxiable = true, kind = DnsValueKind.CONTENT),
    TXT(proxiable = false, kind = DnsValueKind.CONTENT),
    NS(proxiable = false, kind = DnsValueKind.CONTENT),
    PTR(proxiable = false, kind = DnsValueKind.CONTENT),
    MX(proxiable = false, kind = DnsValueKind.CONTENT_WITH_PRIORITY),
    SRV(proxiable = false, kind = DnsValueKind.STRUCTURED, dataFields = listOf(F_PRIORITY, F_WEIGHT, F_PORT, F_TARGET)),
    CAA(proxiable = false, kind = DnsValueKind.STRUCTURED, dataFields = listOf(F_FLAGS, F_TAG, F_VALUE)),
    HTTPS(proxiable = false, kind = DnsValueKind.STRUCTURED, dataFields = listOf(F_PRIORITY, F_TARGET, F_VALUE)),
    SVCB(proxiable = false, kind = DnsValueKind.STRUCTURED, dataFields = listOf(F_PRIORITY, F_TARGET, F_VALUE)),
    ;

    companion object {
        /** Types offered in the create form, in display order. */
        val creatable: List<DnsRecordType> = entries

        /** Known type for a wire name, or null when Virga has no form for it. */
        fun fromWire(raw: String?): DnsRecordType? = entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }

    }
}

/**
 * The form state for create and edit. Everything is text until it is validated, so typing never loses input.
 * [values] holds the per-type fields keyed by the names in [DnsRecordType.dataFields] or `content` / `priority`.
 */
data class DnsRecordForm(
    val type: String = DnsRecordType.A.name,
    val name: String = "",
    val ttl: String = AUTO_TTL.toString(),
    val proxied: Boolean = false,
    val comment: String = "",
    val tags: String = "",
    val values: Map<String, String> = emptyMap(),
) {
    val recordType: DnsRecordType? get() = DnsRecordType.fromWire(type)

    /** Changing type resets the value fields, because they mean something different per type. Name, TTL and comment stay. */
    fun withType(next: DnsRecordType): DnsRecordForm = copy(
        type = next.name,
        values = emptyMap(),
        proxied = proxied && next.proxiable,
    )

    fun withValue(key: String, value: String): DnsRecordForm = copy(values = values + (key to value))

    /** Hides the TTL input when the record is proxied, because the API forces Auto for proxied records. */
    val ttlIsForcedAuto: Boolean get() = proxied && recordType?.proxiable == true
}

/** Keys of the structured `data` object and of the form values for SRV, CAA, HTTPS and SVCB. */
const val F_PRIORITY = "priority"
const val F_WEIGHT = "weight"
const val F_PORT = "port"
const val F_TARGET = "target"
const val F_FLAGS = "flags"
const val F_TAG = "tag"
const val F_VALUE = "value"
