package com.tomasps.virga.feature.domains.dns

import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.core.ui.state.UiText

/** The record being created (recordId null) or edited. */
data class DnsEditor(
    val recordId: String?,
    val form: DnsRecordForm,
)

/**
 * State of the DNS records screen. [records] holds the pages loaded so far. [query] filters the loaded rows on the
 * device; [typeFilter] is sent to the API, so it reloads the first page. [pendingDeleteIds] is set while a delete
 * waits for the owner's confirmation; one id is a single delete, several are the bulk batch.
 */
data class DnsRecordsUiState(
    val records: UiState<List<DnsRecord>> = UiState.Loading,
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
    val loadMoreFailed: Boolean = false,
    val query: String = "",
    val typeFilter: String? = null,
    val selectedIds: Set<String> = emptySet(),
    val isPaidPlan: Boolean = false,
    val editor: DnsEditor? = null,
    val formErrors: List<DnsFieldError> = emptyList(),
    val saving: Boolean = false,
    val pendingDeleteIds: List<String>? = null,
    val busy: Boolean = false,
    /** True while a pull-to-refresh reload is in flight. */
    val refreshing: Boolean = false,
    val message: UiText? = null,
    /** BIND text waiting for the owner to pick where to save it. Null when no export is pending. */
    val exportText: String? = null,
) {
    /** Loaded records whose name or content contains the query, in the order they were loaded. */
    fun visibleRecords(): List<DnsRecord> = (records as? UiState.Content)?.data
        ?.filter { record -> record.matchesSearch(query) }
        .orEmpty()

    val selectionMode: Boolean get() = selectedIds.isNotEmpty()
}

/** True when the record's name or content contains the search text. An empty search matches every record. */
fun DnsRecord.matchesSearch(query: String): Boolean {
    val needle = query.trim().lowercase()
    return needle.isEmpty() ||
        name.lowercase().contains(needle) ||
        content.orEmpty().lowercase().contains(needle)
}

/**
 * The type filter after a save. A saved record of the filtered type keeps the filter; a record of another type would
 * be hidden by it, so the filter is dropped and the new row shows.
 */
fun filterAfterSave(filter: String?, savedType: String): String? =
    if (filter == null || filter.equals(savedType, ignoreCase = true)) filter else null

/** The search text after a save. A saved record the search would hide clears the search, so the new row shows. */
fun queryAfterSave(query: String, saved: DnsRecord): String = if (saved.matchesSearch(query)) query else ""
