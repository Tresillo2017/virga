package com.tomasps.virga.feature.kv.addkey

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomasps.virga.core.auth.session.SessionRepository
import com.tomasps.virga.core.auth.session.selectedAccountIds
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.ui.state.UiText
import com.tomasps.virga.feature.kv.R
import com.tomasps.virga.feature.kv.changes.KvNamespaceChanges
import com.tomasps.virga.feature.kv.data.KvRepository
import com.tomasps.virga.feature.kv.files.KvFileStore
import com.tomasps.virga.feature.kv.key.KvKeyWriter
import com.tomasps.virga.feature.kv.key.KvWriteResult
import com.tomasps.virga.feature.kv.key.expirationInputText
import com.tomasps.virga.feature.kv.key.expirationProblemText
import com.tomasps.virga.feature.kv.key.metadataProblemText
import com.tomasps.virga.feature.kv.kvActionMessage
import com.tomasps.virga.feature.kv.tryUpdate
import com.tomasps.virga.feature.kv.value.ExpirationParse
import com.tomasps.virga.feature.kv.value.KV_MAX_VALUE_BYTES
import com.tomasps.virga.feature.kv.value.KeyNameProblem
import com.tomasps.virga.feature.kv.value.KvExpiration
import com.tomasps.virga.feature.kv.value.KvExpirationDraft
import com.tomasps.virga.feature.kv.value.KvExpirationMode
import com.tomasps.virga.feature.kv.value.MetadataCheck
import com.tomasps.virga.feature.kv.value.parseExpirationDraft
import com.tomasps.virga.feature.kv.value.validateExpiration
import com.tomasps.virga.feature.kv.value.validateKeyName
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
import java.time.Instant
import java.time.ZoneId

/** Where the new value comes from: text typed on the screen, or a document the user picks. */
enum class KvAddKeySource { TEXT, FILE }

data class KvAddKeyUiState(
    val name: String = "",
    val source: KvAddKeySource = KvAddKeySource.TEXT,
    val text: String = "",
    val fileUri: String? = null,
    val fileName: String = "",
    val fileSize: Long? = null,
    val metadataText: String = "",
    val expirationDraft: KvExpirationDraft = KvExpirationDraft(KvExpirationMode.NONE, ""),
    val isBusy: Boolean = false,
    val showOverwrite: Boolean = false,
)

sealed interface KvAddKeyEvent {
    data class Message(val text: UiText) : KvAddKeyEvent

    /** The key was written; the screen should close. */
    data class Saved(val keyName: String) : KvAddKeyEvent
}

private const val MESSAGE_BUFFER = 4

/**
 * Creates one key from typed text or from a picked file. The name, size, metadata and expiration are checked before
 * anything is sent. An existing key is only replaced after the user confirms.
 */
class KvAddKeyViewModel(
    private val namespaceId: String,
    session: SessionRepository,
    private val repository: KvRepository,
    private val writer: KvKeyWriter,
    private val changes: KvNamespaceChanges,
    private val files: KvFileStore,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val nowEpochSeconds: () -> Long = { Instant.now().epochSecond },
) : ViewModel() {

    private val mutableState = MutableStateFlow(KvAddKeyUiState())
    val state: StateFlow<KvAddKeyUiState> = mutableState.asStateFlow()

    private val mutableEvents = MutableSharedFlow<KvAddKeyEvent>(extraBufferCapacity = MESSAGE_BUFFER)
    val events: SharedFlow<KvAddKeyEvent> = mutableEvents.asSharedFlow()

    private var accountId: String? = null

    init {
        viewModelScope.launch {
            session.selectedAccountIds().collectLatest { accountId = it }
        }
    }

    fun setName(name: String) = mutableState.update { it.copy(name = name, showOverwrite = false) }

    fun setText(text: String) = mutableState.update { it.copy(text = text) }

    fun setSource(source: KvAddKeySource) = mutableState.update { it.copy(source = source) }

    fun setMetadata(text: String) = mutableState.update { it.copy(metadataText = text) }

    fun setExpiration(draft: KvExpirationDraft) = mutableState.update { it.copy(expirationDraft = draft) }

    /** Remembers the picked document and its size, so the limit can be shown before saving. */
    fun pickFile(uri: String?) {
        if (uri == null) return
        viewModelScope.launch {
            val size = files.sizeOf(uri)
            val name = files.displayNameOf(uri).orEmpty()
            mutableState.update { it.copy(source = KvAddKeySource.FILE, fileUri = uri, fileName = name, fileSize = size) }
        }
    }

    fun dismissOverwrite() = mutableState.update { it.copy(showOverwrite = false) }

    fun save() = startSave(overwriteConfirmed = false)

    /** Called after the user agrees to replace the existing key. */
    fun confirmOverwrite() {
        mutableState.update { it.copy(showOverwrite = false) }
        startSave(overwriteConfirmed = true)
    }

    /**
     * Starts one save. The busy flag is set before the coroutine is launched, so a second tap that arrives while the
     * first save is still checking the key is ignored rather than writing or prompting twice.
     */
    private fun startSave(overwriteConfirmed: Boolean) {
        if (!mutableState.tryUpdate(canStart = { !it.isBusy }, change = { it.copy(isBusy = true) })) return
        viewModelScope.launch {
            try {
                val id = accountId
                if (id == null) emit(UiText.Resource(R.string.kv_error_no_account)) else saveChecked(id, overwriteConfirmed)
            } finally {
                mutableState.update { it.copy(isBusy = false) }
            }
        }
    }

    private suspend fun saveChecked(id: String, overwriteConfirmed: Boolean) {
        val draft = mutableState.value
        val name = draft.name
        if (!checkName(name)) return
        val value = readValueBytes(draft) ?: return
        val metadata = when (val check = validateMetadataText(draft.metadataText)) {
            is MetadataCheck.Invalid -> return emit(metadataProblemText(check.problem))
            is MetadataCheck.Valid -> check.element
        }
        val expiration = expirationOrNull(draft.expirationDraft) ?: return
        if (!overwriteConfirmed && !confirmedNew(id, name)) return
        val result = writer.write(id, namespaceId, name, value, metadata, expiration)
        when (result) {
            is KvWriteResult.Written -> {
                changes.notifyChanged()
                mutableEvents.emit(KvAddKeyEvent.Saved(name))
            }
            is KvWriteResult.Throttled -> emit(UiText.Resource(R.string.kv_error_key_write_rate_limited))
            is KvWriteResult.ExpiresTooSoon -> emit(UiText.Resource(R.string.kv_error_expires_too_soon))
            is KvWriteResult.Failed -> emit(kvActionMessage(result.error, isSingleKeyWrite = true))
        }
    }

    /** True when the name may be used. Otherwise the reason is shown and false is returned. */
    private fun checkName(name: String): Boolean {
        val problem = validateKeyName(name) ?: return true
        emit(
            when (problem) {
                KeyNameProblem.EMPTY -> UiText.Resource(R.string.kv_error_key_empty)
                KeyNameProblem.DOT_SEGMENT -> UiText.Resource(R.string.kv_error_key_dot_segment)
                KeyNameProblem.TOO_LONG -> UiText.Resource(R.string.kv_error_key_too_long)
            },
        )
        return false
    }

    /** The bytes to write, or null after reporting why the value cannot be sent. */
    private suspend fun readValueBytes(draft: KvAddKeyUiState): ByteArray? = when (draft.source) {
        KvAddKeySource.TEXT -> draft.text.encodeToByteArray().takeIf { bytes -> sizeOk(bytes.size.toLong()) }
        KvAddKeySource.FILE -> readFileBytes(draft.fileUri)
    }

    private suspend fun readFileBytes(uri: String?): ByteArray? {
        if (uri == null) {
            emit(UiText.Resource(R.string.kv_error_no_file))
            return null
        }
        val size = files.sizeOf(uri)
        if (size == null) {
            emit(UiText.Resource(R.string.kv_error_file_unreadable))
            return null
        }
        if (!sizeOk(size)) return null
        val bytes = files.readBytes(uri, KV_MAX_VALUE_BYTES)
        if (bytes == null) {
            emit(UiText.Resource(R.string.kv_error_file_unreadable))
            return null
        }
        return bytes
    }

    private fun sizeOk(sizeBytes: Long): Boolean {
        if (validateValueSize(sizeBytes) == null) return true
        emit(UiText.Resource(R.string.kv_error_value_too_large))
        return false
    }

    /** The expiration to send, or null after reporting why the draft is not usable. */
    private fun expirationOrNull(draft: KvExpirationDraft): KvExpiration? {
        val expiration = when (val parsed = parseExpirationDraft(draft, zone)) {
            is ExpirationParse.Invalid -> {
                emit(expirationInputText(parsed.problem))
                return null
            }
            is ExpirationParse.Ok -> parsed.expiration
        }
        val problem = validateExpiration(expiration, nowEpochSeconds())
        if (problem != null) {
            emit(expirationProblemText(problem))
            return null
        }
        return expiration
    }

    /**
     * True when the key does not exist yet and the write may go ahead. When it exists, the overwrite prompt is shown and
     * false is returned. When the lookup fails, nothing is written, because the overwrite cannot be ruled out.
     */
    private suspend fun confirmedNew(id: String, name: String): Boolean =
        when (val lookup = repository.findKey(id, namespaceId, name)) {
            is ApiResult.Failure -> {
                emit(UiText.Resource(R.string.kv_error_overwrite_check_failed))
                false
            }
            is ApiResult.Success -> if (lookup.value == null) {
                true
            } else {
                mutableState.update { it.copy(showOverwrite = true) }
                false
            }
        }

    private fun emit(text: UiText) {
        mutableEvents.tryEmit(KvAddKeyEvent.Message(text))
    }
}
