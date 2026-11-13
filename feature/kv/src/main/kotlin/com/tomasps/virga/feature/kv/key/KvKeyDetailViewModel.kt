package com.tomasps.virga.feature.kv.key

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomasps.virga.core.auth.session.SessionRepository
import com.tomasps.virga.core.auth.session.selectedAccountIds
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.ui.state.ApiErrorUiMapper
import com.tomasps.virga.core.ui.state.UiState
import com.tomasps.virga.core.ui.state.UiText
import com.tomasps.virga.feature.kv.R
import com.tomasps.virga.feature.kv.changes.KvNamespaceChanges
import com.tomasps.virga.feature.kv.data.KvRepository
import com.tomasps.virga.feature.kv.files.KvFileStore
import com.tomasps.virga.feature.kv.kvActionMessage
import com.tomasps.virga.feature.kv.tryUpdate
import com.tomasps.virga.feature.kv.value.KV_MAX_VALUE_BYTES
import com.tomasps.virga.feature.kv.value.ExpirationParse
import com.tomasps.virga.feature.kv.value.KvExpiration
import com.tomasps.virga.feature.kv.value.KvExpirationDraft
import com.tomasps.virga.feature.kv.value.KvExpirationMode
import com.tomasps.virga.feature.kv.value.KvValueView
import com.tomasps.virga.feature.kv.value.classifyValue
import com.tomasps.virga.feature.kv.value.decodeUtf8OrNull
import com.tomasps.virga.feature.kv.value.MetadataCheck
import com.tomasps.virga.feature.kv.value.parseExpirationDraft
import com.tomasps.virga.feature.kv.value.validateExpiration
import com.tomasps.virga.feature.kv.value.validateMetadataText
import com.tomasps.virga.feature.kv.value.validateValueSize
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.time.Instant
import java.time.ZoneId

/** Expiration and metadata of one key, as the key list and the metadata route report them. */
data class KvKeyDetails(
    val expiresAtEpochSeconds: Long?,
    val metadata: JsonElement?,
)

/** Everything the key screen shows. [value] is null until the user asks for the value. */
data class KvKeyUiState(
    val keyName: String = "",
    val isMissing: Boolean = false,
    val details: UiState<KvKeyDetails>? = null,
    val value: UiState<KvValueView>? = null,
    val isBusy: Boolean = false,
    val editText: String? = null,
    val metadataText: String? = null,
    val expirationDraft: KvExpirationDraft? = null,
    val showDelete: Boolean = false,
) {
    /** Editing the text is offered only for a value that decoded as text. */
    val canEditText: Boolean
        get() = (value as? UiState.Content)?.data is KvValueView.Text
}

/** One-shot results: a message, the key is gone and the screen should close, or a file name to save under. */
sealed interface KvKeyEvent {
    data class Message(val text: UiText) : KvKeyEvent

    data object Closed : KvKeyEvent

    data class DownloadReady(val suggestedName: String) : KvKeyEvent
}

/** The bytes and the settings one write will send. */
internal data class KvPendingWrite(
    val bytes: ByteArray,
    val metadata: JsonElement?,
    val expiration: KvExpiration,
)

private const val MESSAGE_BUFFER = 4
private const val SLASH = '/'
private val prettyJson = Json { prettyPrint = true }

/**
 * One key: its value (loaded only when asked), metadata, expiration and the actions on them. Every edit is a
 * full rewrite of the value, so each edit re-sends the metadata and the expiration the key already has.
 */
class KvKeyDetailViewModel(
    private val namespaceId: String,
    private val keyName: String,
    session: SessionRepository,
    private val repository: KvRepository,
    private val writer: KvKeyWriter,
    private val changes: KvNamespaceChanges,
    private val files: KvFileStore,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val nowEpochSeconds: () -> Long = { Instant.now().epochSecond },
) : ViewModel() {

    private val mutableState = MutableStateFlow(KvKeyUiState(keyName = keyName))
    val state: StateFlow<KvKeyUiState> = mutableState.asStateFlow()

    private val mutableEvents = MutableSharedFlow<KvKeyEvent>(extraBufferCapacity = MESSAGE_BUFFER)
    val events: SharedFlow<KvKeyEvent> = mutableEvents.asSharedFlow()

    private var accountId: String? = null

    /** The bytes last read or written, kept out of the state so the UI state stays comparable. */
    private var loadedBytes: ByteArray? = null

    init {
        viewModelScope.launch {
            session.selectedAccountIds().collectLatest { id ->
                accountId = id
                forgetValue()
                loadDetails(id)
            }
        }
    }

    /** Reloads the details and forgets the value, so the next value or edit reads the stored bytes again. */
    fun retry() {
        val id = accountId ?: return
        forgetValue()
        viewModelScope.launch { loadDetails(id) }
    }

    /** Reads the value on request. Values above the 25 MiB limit are refused rather than read. */
    fun showValue() {
        val id = accountId ?: return
        viewModelScope.launch { ensureBytes(id, fresh = false) }
    }

    fun startEdit() {
        val bytes = loadedBytes ?: return
        val text = decodeUtf8OrNull(bytes) ?: return
        mutableState.update { it.copy(editText = text) }
    }

    fun updateEditText(text: String) = mutableState.update { it.copy(editText = text) }

    fun cancelEditors() = mutableState.update {
        it.copy(editText = null, metadataText = null, expirationDraft = null, showDelete = false)
    }

    fun saveEdit() = launchWrite { _, details ->
        val text = mutableState.value.editText ?: return@launchWrite null
        val bytes = text.encodeToByteArray()
        if (validateValueSize(bytes.size.toLong()) != null) {
            emitMessage(UiText.Resource(R.string.kv_error_value_too_large))
            return@launchWrite null
        }
        keepExpirationOrRefuse(details)?.let { KvPendingWrite(bytes, details.metadata, it) }
    }

    fun openMetadataEditor() {
        val details = contentDetails() ?: return emitDetailsUnavailable()
        val pretty = details.metadata?.let { prettyJson.encodeToString(JsonElement.serializer(), it) }.orEmpty()
        mutableState.update { it.copy(metadataText = pretty) }
    }

    fun updateMetadataText(text: String) = mutableState.update { it.copy(metadataText = text) }

    fun saveMetadata() = launchWrite { id, details ->
        val text = mutableState.value.metadataText ?: return@launchWrite null
        val check = validateMetadataText(text)
        if (check is MetadataCheck.Invalid) {
            emitMessage(metadataProblemText(check.problem))
            return@launchWrite null
        }
        val bytes = ensureBytes(id, fresh = true) ?: return@launchWrite null
        val element = (check as MetadataCheck.Valid).element
        keepExpirationOrRefuse(details)?.let { KvPendingWrite(bytes, element, it) }
    }

    fun openExpirationEditor() {
        val details = contentDetails() ?: return emitDetailsUnavailable()
        val draft = details.expiresAtEpochSeconds?.let { epoch ->
            KvExpirationDraft(KvExpirationMode.ABSOLUTE, formatAbsolute(epoch))
        } ?: KvExpirationDraft(KvExpirationMode.NONE, "")
        mutableState.update { it.copy(expirationDraft = draft) }
    }

    fun updateExpirationDraft(draft: KvExpirationDraft) = mutableState.update { it.copy(expirationDraft = draft) }

    fun saveExpiration() = launchWrite { id, details ->
        val draft = mutableState.value.expirationDraft ?: return@launchWrite null
        val expiration = when (val parsed = parseExpirationDraft(draft, zone)) {
            is ExpirationParse.Invalid -> {
                emitMessage(expirationInputText(parsed.problem))
                return@launchWrite null
            }
            is ExpirationParse.Ok -> parsed.expiration
        }
        validateExpiration(expiration, nowEpochSeconds())?.let { problem ->
            emitMessage(expirationProblemText(problem))
            return@launchWrite null
        }
        val bytes = ensureBytes(id, fresh = true) ?: return@launchWrite null
        KvPendingWrite(bytes, details.metadata, expiration)
    }

    fun requestDelete() = mutableState.update { it.copy(showDelete = true) }

    fun dismissDelete() = mutableState.update { it.copy(showDelete = false) }

    fun delete() {
        val id = accountId ?: return
        viewModelScope.launch {
            mutableState.update { it.copy(isBusy = true, showDelete = false) }
            when (val result = repository.deleteValue(id, namespaceId, keyName)) {
                is ApiResult.Success -> {
                    changes.notifyChanged()
                    mutableEvents.emit(KvKeyEvent.Closed)
                }
                is ApiResult.Failure -> {
                    mutableState.update { it.copy(isBusy = false) }
                    emitMessage(kvActionMessage(result.error, isSingleKeyWrite = true))
                }
            }
        }
    }

    /** Loads the value if needed, then asks the screen to open the save dialog. */
    fun prepareDownload() {
        val id = accountId ?: return
        viewModelScope.launch {
            if (ensureBytes(id, fresh = false) != null) mutableEvents.emit(KvKeyEvent.DownloadReady(suggestedFileName(keyName)))
        }
    }

    /** Writes the loaded bytes to the document the user picked. */
    fun saveDownload(uri: String?) {
        val bytes = loadedBytes ?: return
        if (uri == null) return
        viewModelScope.launch {
            val saved = files.writeBytes(uri, bytes)
            emitMessage(
                if (saved) UiText.Resource(R.string.kv_message_downloaded) else UiText.Resource(R.string.kv_error_download_failed),
            )
        }
    }

    /** One edit at a time: the busy flag is set before the launch, so a second tap during the checks is ignored. */
    private fun launchWrite(prepare: suspend (accountId: String, details: KvKeyDetails) -> KvPendingWrite?) {
        if (!mutableState.tryUpdate(canStart = { !it.isBusy }, change = { it.copy(isBusy = true) })) return
        viewModelScope.launch {
            try {
                val id = accountId ?: return@launch
                val details = contentDetails() ?: return@launch emitDetailsUnavailable()
                val pending = prepare(id, details) ?: return@launch
                val result = writer.write(id, namespaceId, keyName, pending.bytes, pending.metadata, pending.expiration)
                finishWrite(id, pending.bytes, result)
            } finally {
                mutableState.update { it.copy(isBusy = false) }
            }
        }
    }

    private suspend fun finishWrite(id: String, bytes: ByteArray, result: KvWriteResult) {
        when (result) {
            is KvWriteResult.Written -> {
                loadedBytes = bytes
                changes.notifyChanged()
                mutableState.update {
                    it.copy(
                        isBusy = false,
                        value = UiState.Content(classifyValue(bytes)),
                        editText = null,
                        metadataText = null,
                        expirationDraft = null,
                    )
                }
                loadDetails(id)
                emitMessage(UiText.Resource(R.string.kv_message_value_saved))
            }
            is KvWriteResult.Throttled -> {
                mutableState.update { it.copy(isBusy = false) }
                emitMessage(UiText.Resource(R.string.kv_error_key_write_rate_limited))
            }
            is KvWriteResult.ExpiresTooSoon -> {
                mutableState.update { it.copy(isBusy = false) }
                emitMessage(UiText.Resource(R.string.kv_error_expires_too_soon))
            }
            is KvWriteResult.Failed -> {
                mutableState.update { it.copy(isBusy = false) }
                emitMessage(kvActionMessage(result.error, isSingleKeyWrite = true))
            }
        }
    }

    /** The expiration an edit re-sends, or null after telling the user why the edit was refused. */
    private suspend fun keepExpirationOrRefuse(details: KvKeyDetails): KvExpiration? =
        when (val keep = writer.expirationToKeep(details.expiresAtEpochSeconds)) {
            is KvExpirationKeep.Keep -> keep.expiration
            is KvExpirationKeep.TooSoon -> {
                emitMessage(UiText.Resource(R.string.kv_error_expires_too_soon))
                null
            }
        }

    private suspend fun loadDetails(id: String) {
        mutableState.update { it.copy(details = if (it.details is UiState.Content) it.details else UiState.Loading) }
        val found = when (val result = repository.findKey(id, namespaceId, keyName)) {
            is ApiResult.Failure -> {
                mutableState.update { it.copy(details = UiState.Failure(ApiErrorUiMapper.toErrorUi(result.error))) }
                return
            }
            is ApiResult.Success -> result.value
        }
        if (found == null) {
            mutableState.update { it.copy(isMissing = true, details = null) }
            return
        }
        val metadata = when (val result = repository.metadata(id, namespaceId, keyName)) {
            is ApiResult.Failure -> {
                mutableState.update { it.copy(details = UiState.Failure(ApiErrorUiMapper.toErrorUi(result.error))) }
                return
            }
            is ApiResult.Success -> result.value
        }
        mutableState.update {
            it.copy(
                isMissing = false,
                details = UiState.Content(KvKeyDetails(found.expirationEpochSeconds, metadata)),
            )
        }
    }

    /** Drops the cached value and its screen state. */
    private fun forgetValue() {
        loadedBytes = null
        mutableState.update { it.copy(value = null) }
    }

    /**
     * Returns the value's bytes. A cached copy is used unless [fresh] is set. An edit that rewrites the whole value sets
     * [fresh], because a copy read earlier may be stale and would otherwise be written back over a newer value.
     * Returns null after reporting why the value is not available.
     */
    private suspend fun ensureBytes(id: String, fresh: Boolean): ByteArray? {
        if (!fresh) loadedBytes?.let { return it }
        mutableState.update { it.copy(value = UiState.Loading) }
        return when (val result = repository.readValue(id, namespaceId, keyName, KV_MAX_VALUE_BYTES)) {
            is ApiResult.Failure -> {
                mutableState.update { it.copy(value = UiState.Failure(ApiErrorUiMapper.toErrorUi(result.error))) }
                null
            }
            is ApiResult.Success -> {
                val bytes = result.value
                if (bytes == null) {
                    mutableState.update { it.copy(value = null) }
                    emitMessage(UiText.Resource(R.string.kv_error_value_too_large))
                    null
                } else {
                    loadedBytes = bytes
                    mutableState.update { it.copy(value = UiState.Content(classifyValue(bytes))) }
                    bytes
                }
            }
        }
    }

    private fun contentDetails(): KvKeyDetails? = (mutableState.value.details as? UiState.Content)?.data

    private fun emitDetailsUnavailable() = emitMessage(UiText.Resource(R.string.kv_error_details_unavailable))

    private fun emitMessage(text: UiText) {
        mutableEvents.tryEmit(KvKeyEvent.Message(text))
    }

    private fun formatAbsolute(epochSeconds: Long): String =
        java.time.format.DateTimeFormatter.ofPattern(ABSOLUTE_PATTERN)
            .format(Instant.ofEpochSecond(epochSeconds).atZone(zone))

    private companion object {
        const val ABSOLUTE_PATTERN = "yyyy-MM-dd HH:mm"
    }
}

/** A file name for the save dialog. Slashes would read as folders, so they become underscores. */
internal fun suggestedFileName(keyName: String): String {
    val safe = keyName.map { if (it == SLASH || it.isISOControl()) '_' else it }.joinToString("")
    return safe.ifBlank { "value" }
}
