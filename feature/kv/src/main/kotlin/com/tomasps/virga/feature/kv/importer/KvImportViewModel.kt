package com.tomasps.virga.feature.kv.importer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomasps.virga.core.auth.session.SessionRepository
import com.tomasps.virga.core.auth.session.selectedAccountIds
import com.tomasps.virga.core.common.DispatcherProvider
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.ui.state.UiText
import com.tomasps.virga.feature.kv.R
import com.tomasps.virga.feature.kv.changes.KvNamespaceChanges
import com.tomasps.virga.feature.kv.data.KvRepository
import com.tomasps.virga.feature.kv.files.KvFileStore
import com.tomasps.virga.feature.kv.kvActionMessage
import com.tomasps.virga.feature.kv.value.BulkImportParse
import com.tomasps.virga.feature.kv.value.BulkImportProblem
import com.tomasps.virga.feature.kv.value.KV_BULK_MAX_BYTES
import com.tomasps.virga.feature.kv.value.ParsedPair
import com.tomasps.virga.feature.kv.value.RejectedPair
import com.tomasps.virga.feature.kv.value.chunkBulkPairs
import com.tomasps.virga.feature.kv.value.parseBulkImport
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray

/** What the file holds, before anything is sent. Keys that failed a local check are listed with the reason. */
data class KvImportPreview(
    val pairCount: Int,
    val chunkCount: Int,
    val rejected: List<RejectedPair>,
)

/** The outcome after a run: keys written, keys the server refused, keys refused locally, and chunks that failed. */
data class KvImportReport(
    val written: Int,
    val unsuccessfulKeys: List<String>,
    val rejected: List<RejectedPair>,
    val failedChunks: Int,
)

data class KvImportUiState(
    val fileName: String = "",
    val hasFile: Boolean = false,
    val refusal: BulkImportProblem? = null,
    val preview: KvImportPreview? = null,
    val isImporting: Boolean = false,
    val chunksDone: Int = 0,
    val report: KvImportReport? = null,
)

sealed interface KvImportEvent {
    data class Message(val text: UiText) : KvImportEvent
}

private const val MESSAGE_BUFFER = 4

/**
 * Bulk import from a JSON file in the bulk-write format. The whole file is parsed and previewed first. Only then are
 * the valid pairs sent, in chunks that stay under the pair and byte limits. A failed chunk is reported, and the rest go on.
 */
class KvImportViewModel(
    private val namespaceId: String,
    session: SessionRepository,
    private val repository: KvRepository,
    private val changes: KvNamespaceChanges,
    private val files: KvFileStore,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val mutableState = MutableStateFlow(KvImportUiState())
    val state: StateFlow<KvImportUiState> = mutableState.asStateFlow()

    private val mutableEvents = MutableSharedFlow<KvImportEvent>(extraBufferCapacity = MESSAGE_BUFFER)
    val events: SharedFlow<KvImportEvent> = mutableEvents.asSharedFlow()

    private var accountId: String? = null
    private var parsedPairs: List<ParsedPair> = emptyList()
    private var parsedRejected: List<RejectedPair> = emptyList()

    init {
        viewModelScope.launch {
            session.selectedAccountIds().collectLatest { accountId = it }
        }
    }

    /** Reads and parses the picked file. A file over the 100 MB limit is refused before it is read. */
    fun pickFile(uri: String?) {
        if (uri == null) return
        viewModelScope.launch {
            val name = files.displayNameOf(uri).orEmpty()
            val size = files.sizeOf(uri)
            if (size == null) {
                reportUnreadable(name, R.string.kv_error_file_unreadable)
                return@launch
            }
            if (size > KV_BULK_MAX_BYTES) {
                reportUnreadable(name, R.string.kv_error_import_file_too_large)
                return@launch
            }
            val bytes = files.readBytes(uri, KV_BULK_MAX_BYTES)
            if (bytes == null) {
                reportUnreadable(name, R.string.kv_error_file_unreadable)
                return@launch
            }
            val parse = withContext(dispatchers.default) { parseBulkImport(bytes.decodeToString()) }
            applyParse(name, parse)
        }
    }

    /**
     * Sends the valid pairs in chunks, in file order. Starts only after a preview with at least one valid pair. The
     * busy flag is set before the launch, so a second tap that arrives before the coroutine runs is ignored.
     */
    fun startImport() {
        val id = accountId ?: return
        val chunks = chunkBulkPairs(parsedPairs)
        if (chunks.isEmpty() || mutableState.value.isImporting) return
        mutableState.update { it.copy(isImporting = true, chunksDone = 0, report = null) }
        viewModelScope.launch {
            val tally = ImportTally()
            for (chunk in chunks) {
                tally.add(sendChunk(id, chunk))
                mutableState.update { it.copy(chunksDone = it.chunksDone + 1) }
            }
            mutableState.update { it.copy(isImporting = false, report = tally.toReport(parsedRejected)) }
            changes.notifyChanged()
        }
    }

    private suspend fun sendChunk(id: String, chunk: List<ParsedPair>): ChunkResult {
        val body = JsonArray(chunk.map { it.element })
        return when (val result = repository.bulkWrite(id, namespaceId, body)) {
            is ApiResult.Success -> ChunkResult.Sent(result.value.successfulCount, result.value.unsuccessfulKeys)
            is ApiResult.Failure -> {
                mutableEvents.tryEmit(KvImportEvent.Message(kvActionMessage(result.error, isSingleKeyWrite = false)))
                ChunkResult.Failed(chunk.map { it.key })
            }
        }
    }

    private fun applyParse(name: String, parse: BulkImportParse) {
        parsedPairs = parse.pairs
        parsedRejected = parse.rejected
        mutableState.update {
            it.copy(
                fileName = name,
                hasFile = true,
                refusal = parse.problem,
                preview = if (parse.problem != null) null else KvImportPreview(
                    pairCount = parse.pairs.size,
                    chunkCount = chunkBulkPairs(parse.pairs).size,
                    rejected = parse.rejected,
                ),
                report = null,
            )
        }
    }

    /** A file that could not be read or is over the size limit: no preview, and the reason is shown. */
    private fun reportUnreadable(name: String, messageId: Int) {
        parsedPairs = emptyList()
        parsedRejected = emptyList()
        mutableState.update { it.copy(fileName = name, hasFile = true, refusal = null, preview = null, report = null) }
        mutableEvents.tryEmit(KvImportEvent.Message(UiText.Resource(messageId)))
    }
}

/** What one chunk did: the server's count and the keys it refused, or every key in the chunk when the request failed. */
private sealed interface ChunkResult {
    data class Sent(val successful: Int, val unsuccessful: List<String>) : ChunkResult

    data class Failed(val keys: List<String>) : ChunkResult
}

/** Running totals across chunks, kept apart from the state so the state stays a plain copy. */
private class ImportTally {
    private var written = 0
    private val unsuccessful = mutableListOf<String>()
    private var failedChunks = 0

    fun add(result: ChunkResult) {
        when (result) {
            is ChunkResult.Sent -> {
                written += result.successful
                unsuccessful += result.unsuccessful
            }
            is ChunkResult.Failed -> {
                failedChunks++
                unsuccessful += result.keys
            }
        }
    }

    fun toReport(rejected: List<RejectedPair>) = KvImportReport(
        written = written,
        unsuccessfulKeys = unsuccessful.toList(),
        rejected = rejected,
        failedChunks = failedChunks,
    )
}
