package com.tomasps.virga.wear.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomasps.virga.core.common.builds.BuildSummary
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.wear.builds.WearBuilds
import com.tomasps.virga.wear.session.WatchDisconnect
import com.tomasps.virga.wear.session.WatchSessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

sealed interface ListState {
    /** Also shown while the session is still restoring. */
    data object Loading : ListState
    data object NotConnected : ListState
    data object Stale : ListState

    /** The wearer tapped Disconnect on the stale screen and has not confirmed yet. Nothing is cleared until then. */
    data object ConfirmDisconnect : ListState
    data object Empty : ListState
    data class Content(val builds: List<BuildSummary>) : ListState
    data class Error(val kind: ErrorKind) : ListState
}

/**
 * The recent builds for the phone's selected account. The account comes from the connected session and the list
 * reloads whenever the session changes. A session that is not connected never reaches the API. A 401 is handled
 * inside [WearBuilds] (it wipes the session), so this only shows it.
 */
class BuildListViewModel(
    private val session: StateFlow<WatchSessionState>,
    private val builds: WearBuilds,
    private val disconnector: WatchDisconnect,
) : ViewModel() {

    private val mutableState = MutableStateFlow<ListState>(ListState.Loading)
    val state: StateFlow<ListState> = mutableState.asStateFlow()

    private val refreshTick = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            combine(session, refreshTick) { current, _ -> current }.collectLatest(::show)
        }
    }

    /** Reads the list again, for the error screen's retry. */
    fun refresh() {
        refreshTick.value += 1
    }

    /** Disconnect on the stale screen only asks; [confirmDisconnect] clears, [dismissDisconnect] backs out. */
    fun askDisconnect() {
        if (mutableState.value == ListState.Stale) mutableState.value = ListState.ConfirmDisconnect
    }

    fun dismissDisconnect() {
        if (mutableState.value == ListState.ConfirmDisconnect) mutableState.value = ListState.Stale
    }

    /** Forgets the token. The session then moves to NotConnected and the connect message shows. */
    fun confirmDisconnect() {
        if (mutableState.value != ListState.ConfirmDisconnect) return
        viewModelScope.launch {
            try {
                disconnector.disconnect()
            } catch (error: CancellationException) {
                throw error
            } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
                // A wipe always disconnects in memory before it touches storage; only the class name is logged.
                Log.w("VirgaWear", "Disconnect failed: ${error::class.java.name}")
            }
        }
    }

    private suspend fun show(current: WatchSessionState) {
        when (current) {
            WatchSessionState.Restoring -> mutableState.value = ListState.Loading
            WatchSessionState.NotConnected -> mutableState.value = ListState.NotConnected
            WatchSessionState.Stale -> mutableState.value = ListState.Stale
            is WatchSessionState.Connected -> load(current.accountId)
        }
    }

    private suspend fun load(accountId: String) {
        mutableState.value = ListState.Loading
        mutableState.value = when (val result = builds.recent(accountId)) {
            is ApiResult.Failure -> ListState.Error(result.error.toErrorKind())
            is ApiResult.Success ->
                if (result.value.isEmpty()) ListState.Empty else ListState.Content(result.value)
        }
    }
}
