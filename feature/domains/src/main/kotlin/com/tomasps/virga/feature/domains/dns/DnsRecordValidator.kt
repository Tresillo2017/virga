package com.tomasps.virga.feature.domains.dns

/** Why a field is rejected. The UI maps each reason to a string. */
enum class DnsErrorReason {
    REQUIRED, INVALID_IPV4, INVALID_IPV6, INVALID_HOSTNAME, OUT_OF_RANGE, TOO_LONG,
    TTL_OUT_OF_RANGE, SELF_CNAME, TAGS_NOT_ALLOWED, TOO_MANY_TAGS, INVALID_FORMAT, NOT_PROXIABLE, NOT_A_NUMBER,
}

/** A rejected [field]. Field names match the keys of the form (`name`, `content`, `ttl`, `comment`, `tags`, or a data field). */
data class DnsFieldError(val field: String, val reason: DnsErrorReason)

/** Plan limits that the form enforces before sending, so the API does not have to reject the request. */
data class DnsPlanLimits(val isPaid: Boolean) {
    val commentMax: Int get() = if (isPaid) COMMENT_MAX_PAID else COMMENT_MAX_FREE

    private companion object {
        const val COMMENT_MAX_FREE = 100
        const val COMMENT_MAX_PAID = 500
    }
}

private const val NAME_MAX = 255
private const val LABEL_MAX = 63
private const val HOSTNAME_MAX = 253
private const val TTL_MIN = 60
private const val TTL_MAX = 86_400
private const val UINT8_MAX = 255
private const val UINT16_MAX = 65_535
private const val TXT_MAX = 4096
private val IPV4_OCTET = Regex("""(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)""")
private val LABEL = Regex("""[A-Za-z0-9_]([A-Za-z0-9_-]{0,61}[A-Za-z0-9_])?""")
private val IPV6_CHARS = Regex("""[0-9A-Fa-f:.]+""")
private val CAA_TAGS = setOf("issue", "issuewild", "iodef")
private val TAG_PATTERN = Regex("""[^:\s]+:[^:\s].*""")

/**
 * Validates a form before it is sent. Pure: no network, no strings. Returns every problem so the form can show them
 * all at once. An empty list means the form can be sent.
 */
fun validateDnsForm(form: DnsRecordForm, limits: DnsPlanLimits): List<DnsFieldError> {
    val type = form.recordType
    return buildList {
        addAll(validateName(form.name))
        addAll(validateTtl(form))
        addAll(validateProxy(form, type))
        addAll(validateComment(form.comment, limits))
        addAll(validateTags(form, limits))
        addAll(validateValues(form, type))
    }
}

private fun validateName(name: String): List<DnsFieldError> = when {
    name.isBlank() -> listOf(DnsFieldError("name", DnsErrorReason.REQUIRED))
    name.trim().length > NAME_MAX -> listOf(DnsFieldError("name", DnsErrorReason.TOO_LONG))
    else -> emptyList()
}

private fun validateTtl(form: DnsRecordForm): List<DnsFieldError> {
    if (form.ttlIsForcedAuto) return emptyList()
    val ttl = form.ttl.trim().toIntOrNull() ?: return listOf(DnsFieldError("ttl", DnsErrorReason.TTL_OUT_OF_RANGE))
    val ok = ttl == AUTO_TTL || ttl in TTL_MIN..TTL_MAX
    return if (ok) emptyList() else listOf(DnsFieldError("ttl", DnsErrorReason.TTL_OUT_OF_RANGE))
}

private fun validateProxy(form: DnsRecordForm, type: DnsRecordType?): List<DnsFieldError> =
    if (form.proxied && type?.proxiable != true) listOf(DnsFieldError("proxied", DnsErrorReason.NOT_PROXIABLE)) else emptyList()

private fun validateComment(comment: String, limits: DnsPlanLimits): List<DnsFieldError> =
    if (comment.trim().length > limits.commentMax) listOf(DnsFieldError("comment", DnsErrorReason.TOO_LONG)) else emptyList()

private fun validateTags(form: DnsRecordForm, limits: DnsPlanLimits): List<DnsFieldError> {
    val tags = parseTags(form.tags)
    return when {
        tags.isEmpty() -> emptyList()
        !limits.isPaid -> listOf(DnsFieldError("tags", DnsErrorReason.TAGS_NOT_ALLOWED))
        tags.size > MAX_TAGS_PER_RECORD -> listOf(DnsFieldError("tags", DnsErrorReason.TOO_MANY_TAGS))
        tags.any { !TAG_PATTERN.matches(it) } -> listOf(DnsFieldError("tags", DnsErrorReason.INVALID_FORMAT))
        else -> emptyList()
    }
}

private fun validateValues(form: DnsRecordForm, type: DnsRecordType?): List<DnsFieldError> {
    val v = form.values.mapValues { it.value.trim() }
    if (type == null) return validateGeneric(v)
    return when (type.kind) {
        DnsValueKind.CONTENT -> validateContent(type, v[CONTENT_FIELD].orEmpty()) +
            if (type == DnsRecordType.CNAME) validateSelfCname(form.name, v[CONTENT_FIELD].orEmpty()) else emptyList()
        DnsValueKind.CONTENT_WITH_PRIORITY ->
            validateHostname(CONTENT_FIELD, v[CONTENT_FIELD].orEmpty()) + validatePriority(v[PRIORITY_FIELD].orEmpty())
        DnsValueKind.STRUCTURED -> validateStructured(type, v)
        DnsValueKind.GENERIC -> validateGeneric(v)
    }
}

/** Unknown record types: only the content has to be present, because Virga cannot check its format. */
private fun validateGeneric(v: Map<String, String>): List<DnsFieldError> =
    if (v[CONTENT_FIELD].isNullOrBlank()) listOf(DnsFieldError(CONTENT_FIELD, DnsErrorReason.REQUIRED)) else emptyList()

private const val CONTENT_FIELD = "content"
private const val PRIORITY_FIELD = "priority"

private fun validateContent(type: DnsRecordType, content: String): List<DnsFieldError> = when (type) {
    DnsRecordType.A -> validateIpv4(content)
    DnsRecordType.AAAA -> validateIpv6(content)
    DnsRecordType.TXT -> when {
        content.isEmpty() -> listOf(DnsFieldError(CONTENT_FIELD, DnsErrorReason.REQUIRED))
        content.length > TXT_MAX -> listOf(DnsFieldError(CONTENT_FIELD, DnsErrorReason.TOO_LONG))
        else -> emptyList()
    }
    DnsRecordType.CNAME -> validateHostname(CONTENT_FIELD, content)
    else -> validateHostname(CONTENT_FIELD, content)
}

private fun validateIpv4(value: String): List<DnsFieldError> {
    val octets = value.split('.')
    val ok = octets.size == 4 && octets.all { IPV4_OCTET.matches(it) }
    return if (ok) emptyList() else listOf(DnsFieldError(CONTENT_FIELD, DnsErrorReason.INVALID_IPV4))
}

private fun validateIpv6(value: String): List<DnsFieldError> {
    val ok = value.contains(':') && value.length <= IPV6_MAX && IPV6_CHARS.matches(value)
    return if (ok) emptyList() else listOf(DnsFieldError(CONTENT_FIELD, DnsErrorReason.INVALID_IPV6))
}

private const val IPV6_MAX = 45

/** A hostname or a dotted name. A trailing dot is allowed (fully qualified). */
private fun validateHostname(field: String, value: String): List<DnsFieldError> {
    if (value.isEmpty()) return listOf(DnsFieldError(field, DnsErrorReason.REQUIRED))
    val trimmed = value.removeSuffix(".")
    val labels = trimmed.split('.')
    val ok = trimmed.length <= HOSTNAME_MAX && labels.all { it.length <= LABEL_MAX && LABEL.matches(it) }
    return if (ok) emptyList() else listOf(DnsFieldError(field, DnsErrorReason.INVALID_HOSTNAME))
}

private fun validatePriority(raw: String): List<DnsFieldError> {
    val number = raw.toIntOrNull() ?: return listOf(DnsFieldError(PRIORITY_FIELD, DnsErrorReason.NOT_A_NUMBER))
    return if (number in 0..UINT16_MAX) emptyList() else listOf(DnsFieldError(PRIORITY_FIELD, DnsErrorReason.OUT_OF_RANGE))
}

private fun validateStructured(type: DnsRecordType, v: Map<String, String>): List<DnsFieldError> {
    return type.dataFields.flatMap { field -> validateStructuredField(type, field, v[field].orEmpty()) }
}

/** A CNAME may not point at its own name. Compared without the case or the trailing dot. */
private fun validateSelfCname(name: String, content: String): List<DnsFieldError> {
    val same = name.trim().removeSuffix(".").equals(content.removeSuffix("."), ignoreCase = true)
    return if (same && content.isNotEmpty()) listOf(DnsFieldError(CONTENT_FIELD, DnsErrorReason.SELF_CNAME)) else emptyList()
}

private fun validateStructuredField(type: DnsRecordType, field: String, value: String): List<DnsFieldError> {
    if (value.isEmpty() && field != F_VALUE) return listOf(DnsFieldError(field, DnsErrorReason.REQUIRED))
    return when (field) {
        F_TARGET -> validateHostname(field, value)
        F_FLAGS -> rangeCheck(field, value, UINT8_MAX)
        F_PORT, F_WEIGHT, F_PRIORITY -> rangeCheck(field, value, UINT16_MAX)
        F_TAG -> if (type == DnsRecordType.CAA && value !in CAA_TAGS) {
            listOf(DnsFieldError(field, DnsErrorReason.INVALID_FORMAT))
        } else {
            emptyList()
        }
        else -> emptyList()
    }
}

private fun rangeCheck(field: String, value: String, max: Int): List<DnsFieldError> {
    val number = value.toIntOrNull() ?: return listOf(DnsFieldError(field, DnsErrorReason.NOT_A_NUMBER))
    return if (number in 0..max) emptyList() else listOf(DnsFieldError(field, DnsErrorReason.OUT_OF_RANGE))
}
