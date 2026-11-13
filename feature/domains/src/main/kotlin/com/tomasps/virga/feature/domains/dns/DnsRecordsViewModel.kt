package com.tomasps.virga.feature.domains.dns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.common.result.map
import com.tomasps.virga.core.ui.state.ApiErrorUiMapper
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.core.ui.state.UiText
import com.tomasps.virga.feature.domains.R
import com.tomasps.virga.feature.domains.zones.ZoneRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * DNS records of one zone: paged reads, a type filter, bulk selection, create and edit with validation, and deletes
 * that wait for confirmation. Writes reload the first page so the list shows what the API stored.
 *
 * Every reload of the list goes through [reloadList], which cancels the page load in flight and bumps [generation].
 * A page that arrives for an older generation is dropped, so a filter change can never mix in rows of the old filter.
 */
class DnsRecordsViewModel(
    private val zoneId: String,
    private val zones: ZoneRepository,
    private val dns: DnsRecordRepository,
) : ViewModel() {

    private val mutableState = MutableStateFlow(DnsRecordsUiState())
    val state: StateFlow<DnsRecordsUiState> = mutableState.asStateFlow()

    /** The last page loaded. The next request asks for `pageNumber + 1`. */
    private var pageNumber = FIRST_PAGE

    private var listJob: Job? = null
    private var generation = 0

    init {
        viewModelScope.launch { loadPlan() }
        reloadList()
    }

    fun retry() = reloadList()

    fun refresh() {
        mutableState.update { it.copy(refreshing = true) }
        reloadList()
    }

    fun setQuery(query: String) = mutableState.update { it.copy(query = query) }

    fun setTypeFilter(type: String?) {
        if (type == mutableState.value.typeFilter) return
        mutableState.update { it.copy(typeFilter = type) }
        reloadList()
    }


    fun loadMore() {
        val current = mutableState.value
        if (!current.hasMore || current.loadingMore) return
        mutableState.update { it.copy(loadingMore = true, loadMoreFailed = false) }
        val gen = generation
        restartListJob { appendNextPage(gen) }
    }

    fun toggleSelected(recordId: String) = mutableState.update { current ->
        val next = if (recordId in current.selectedIds) current.selectedIds - recordId else current.selectedIds + recordId
        current.copy(selectedIds = next)
    }

    fun clearSelection() = mutableState.update { it.copy(selectedIds = emptySet()) }

    fun requestDelete(recordIds: List<String>) {
        if (recordIds.isNotEmpty()) mutableState.update { it.copy(pendingDeleteIds = recordIds) }
    }

    /** Closes the open editor and asks to delete the record it was editing. Nothing is sent until the dialog confirms. */
    fun requestDeleteEditing() {
        val recordId = mutableState.value.editor?.recordId ?: return
        mutableState.update {
            it.copy(editor = null, formErrors = emptyList(), pendingDeleteIds = listOf(recordId))
        }
    }

    fun dismissDelete() = mutableState.update { it.copy(pendingDeleteIds = null) }

    fun confirmDelete() {
        val ids = mutableState.value.pendingDeleteIds ?: return
        if (mutableState.value.busy) return
        mutableState.update { it.copy(pendingDeleteIds = null, busy = true) }
        viewModelScope.launch { deleteRecords(ids) }
    }

    fun openCreate() = mutableState.update {
        it.copy(editor = DnsEditor(recordId = null, form = DnsRecordForm()), formErrors = emptyList())
    }

    fun openEdit(record: DnsRecord) = mutableState.update {
        it.copy(editor = DnsEditor(recordId = record.id, form = record.toForm()), formErrors = emptyList())
    }

    fun closeEditor() = mutableState.update { it.copy(editor = null, formErrors = emptyList()) }

    /** Replaces the form the editor shows. Errors from the last save stay until the next save. */
    fun updateForm(form: DnsRecordForm) = mutableState.update { current ->
        current.editor?.let { editor -> current.copy(editor = editor.copy(form = form)) } ?: current
    }

    /**
     * Validates the form, then sends the create or the update. A form with problems stays open. A save while one is
     * already in flight is ignored, so a double tap cannot create the record twice.
     */
    fun save() {
        val current = mutableState.value
        val editor = current.editor ?: return
        if (current.saving) return
        val errors = validateDnsForm(editor.form, DnsPlanLimits(isPaid = current.isPaidPlan))
        if (errors.isNotEmpty()) {
            mutableState.update { it.copy(formErrors = errors) }
        } else {
            mutableState.update { it.copy(saving = true, formErrors = emptyList()) }
            viewModelScope.launch { write(editor, current.isPaidPlan) }
        }
    }

    fun messageShown() = mutableState.update { it.copy(message = null) }

    /** Fetches the BIND export. The screen then asks where to save it and calls [exportSaved] afterwards. */
    fun requestExport() {
        if (mutableState.value.busy) return
        mutableState.update { it.copy(busy = true) }
        viewModelScope.launch {
            when (val result = dns.exportBind(zoneId)) {
                is ApiResult.Success -> mutableState.update { it.copy(busy = false, exportText = result.value) }
                is ApiResult.Failure -> mutableState.update { it.copy(busy = false, message = errorText(result.error)) }
            }
        }
    }

    fun exportSaved(saved: Boolean) = mutableState.update {
        val text = if (saved) R.string.domains_message_exported else R.string.domains_message_export_not_saved
        it.copy(exportText = null, message = UiText.Resource(text))
    }

    /**
     * Uploads a BIND file the owner picked, then reloads the records. A file that could not be read or is too large
     * is refused with a message and nothing is sent.
     */
    fun importBind(fileName: String, read: BindFileRead) {
        if (mutableState.value.busy) return
        when (read) {
            BindFileRead.Unreadable -> mutableState.update { it.copy(message = UiText.Resource(R.string.domains_message_import_unreadable)) }
            BindFileRead.TooLarge -> mutableState.update { it.copy(message = UiText.Resource(R.string.domains_message_import_too_large)) }
            is BindFileRead.Content -> startImport(fileName, read.bytes)
        }
    }

    private fun startImport(fileName: String, bytes: ByteArray) {
        mutableState.update { it.copy(busy = true) }
        viewModelScope.launch {
            when (val result = dns.importBind(zoneId, fileName, bytes)) {
                is ApiResult.Success -> {
                    val added = importedCount(result.value)
                    // An import can add records of any type, so the type filter is dropped to show them all.
                    mutableState.update {
                        it.copy(
                            busy = false,
                            typeFilter = null,
                            message = UiText.Resource(R.string.domains_message_imported_count, listOf(added)),
                        )
                    }
                    reloadList()
                }
                is ApiResult.Failure -> mutableState.update { it.copy(busy = false, message = errorText(result.error)) }
            }
        }
    }

    private suspend fun loadPlan() {
        val zone = zones.get(zoneId)
        val paid = (zone as? ApiResult.Success)?.value?.isPaidPlan ?: false
        mutableState.update { it.copy(isPaidPlan = paid) }
    }

    /**
     * Starts a fresh first-page load. The reset is synchronous, so state never shows rows from before the reload, and
     * the page load in flight (if any) is cancelled.
     */
    private fun reloadList() {
        val gen = ++generation
        pageNumber = FIRST_PAGE
        mutableState.update {
            it.copy(
                records = UiState.Loading,
                hasMore = false,
                loadingMore = false,
                loadMoreFailed = false,
                selectedIds = emptySet(),
            )
        }
        restartListJob { fetchFirstPage(gen) }
    }

    private fun restartListJob(block: suspend () -> Unit) {
        listJob?.cancel()
        listJob = viewModelScope.launch { block() }
    }

    private suspend fun fetchFirstPage(gen: Int) {
        val type = mutableState.value.typeFilter
        val result = dns.page(zoneId, FIRST_PAGE, type = type)
        if (gen != generation) return
        mutableState.update { current ->
            when (result) {
                is ApiResult.Success -> current.copy(
                    records = UiState.Content(result.value.records),
                    hasMore = result.value.hasMore,
                    refreshing = false,
                )
                is ApiResult.Failure -> current.copy(
                    records = UiState.Failure(ApiErrorUiMapper.toErrorUi(result.error)),
                    refreshing = false,
                )
            }
        }
    }

    private suspend fun appendNextPage(gen: Int) {
        val next = pageNumber + 1
        val type = mutableState.value.typeFilter
        val result = dns.page(zoneId, next, type = type)
        if (gen != generation) return
        when (result) {
            is ApiResult.Success -> {
                pageNumber = next
                mutableState.update { current ->
                    val existing = (current.records as? UiState.Content)?.data.orEmpty()
                    current.copy(
                        records = UiState.Content(existing + result.value.records),
                        hasMore = result.value.hasMore,
                        loadingMore = false,
                    )
                }
            }
            is ApiResult.Failure -> mutableState.update { it.copy(loadingMore = false, loadMoreFailed = true) }
        }
    }

    private suspend fun write(editor: DnsEditor, isPaid: Boolean) {
        val body = editor.form.toRequestBody(isPaidPlan = isPaid)
        val result = if (editor.recordId == null) {
            dns.create(zoneId, body)
        } else {
            dns.update(zoneId, editor.recordId, body)
        }
        when (result) {
            is ApiResult.Success -> {
                mutableState.update {
                    it.copy(
                        saving = false,
                        editor = null,
                        message = UiText.Resource(R.string.domains_message_record_saved),
                        typeFilter = filterAfterSave(it.typeFilter, result.value.type),
                        query = queryAfterSave(it.query, result.value),
                    )
                }
                reloadList()
            }
            is ApiResult.Failure -> mutableState.update {
                it.copy(saving = false, message = errorText(result.error))
            }
        }
    }

    private suspend fun deleteRecords(ids: List<String>) {
        val result = if (ids.size == 1) {
            dns.delete(zoneId, ids.single()).map { ids }
        } else {
            dns.deleteBatch(zoneId, ids).map { ids }
        }
        when (result) {
            is ApiResult.Success -> mutableState.update { current ->
                val removed = result.value.toSet()
                current.copy(
                    busy = false,
                    selectedIds = current.selectedIds - removed,
                    records = (current.records as? UiState.Content)?.let { content ->
                        UiState.Content(content.data.filterNot { it.id in removed })
                    } ?: current.records,
                    message = UiText.Resource(R.string.domains_message_records_deleted, listOf(result.value.size)),
                )
            }
            is ApiResult.Failure -> mutableState.update { it.copy(busy = false, message = errorText(result.error)) }
        }
    }

    private fun errorText(error: ApiError): UiText = ApiErrorUiMapper.toErrorUi(error).message

    /** The number of records the import added, as the API reports it in `recs_added`. Zero when the reply has none. */
    private fun importedCount(reply: JsonElement?): Int =
        ((reply as? JsonObject)?.get("recs_added") as? JsonPrimitive)?.intOrNull ?: 0

    private companion object {
        const val FIRST_PAGE = 1
    }
}
