package com.tomasps.virga.wear.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomasps.virga.core.common.builds.BuildStatus
import com.tomasps.virga.core.common.builds.BuildSummary
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.wear.builds.RetryPlan
import com.tomasps.virga.wear.builds.WearBuilds
import com.tomasps.virga.wear.session.WatchSessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ActionKind { Cancel, Retry }

sealed interface ActionState {
    data object Idle : ActionState
    data class Confirming(val kind: ActionKind) : ActionState
    data object Running : ActionState
    data class Failed(val message: ErrorKind) : ActionState
    data object Done : ActionState
}

data class DetailState(
    val build: BuildSummary,
    val canCancel: Boolean,
    val retryPlan: RetryPlan?,
    val action: ActionState = ActionState.Idle,
)

/**
 * One build with Cancel or Retry. Both go through a confirm step: [cancel] and [retry] only ask, [confirm] makes the
 * call, [dismiss] backs out. Cancel is offered while the build is active; Retry only for a stopped build whose
 * [RetryPlan] exists. A session that is not connected never reaches the API.
 */
class BuildDetailViewModel(
    private val session: StateFlow<WatchSessionState>,
    private val builds: WearBuilds,
    initial: BuildSummary,
) : ViewModel() {

    /** The account this screen was opened under. Actions never run under a different one. */
    private val pinnedAccountId: String? = (session.value as? WatchSessionState.Connected)?.accountId

    private val mutableState = MutableStateFlow(stateFor(initial, retryPlan = null))
    val state: StateFlow<DetailState> = mutableState.asStateFlow()

    /** True once the session is no longer connected to the pinned account, so the screen should close. */
    val isOrphaned: StateFlow<Boolean> = session
        .map { it.connectedAccountId() != pinnedAccountId || pinnedAccountId == null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, pinnedAccountId == null)

    init {
        viewModelScope.launch { load() }
    }

    fun cancel() = ask(ActionKind.Cancel, allowed = mutableState.value.canCancel)

    fun retry() = ask(ActionKind.Retry, allowed = mutableState.value.retryPlan != null)

    /** Backs out of a confirm step, or leaves a failure or completion screen. */
    fun dismiss() = setAction(ActionState.Idle)

    /** Makes the call that [cancel] or [retry] asked about. Does nothing when nothing is waiting for confirmation. */
    fun confirm() {
        val pending = mutableState.value.action as? ActionState.Confirming ?: return
        val accountId = activeAccountId()
        if (accountId == null) {
            setAction(ActionState.Idle)
            return
        }
        setAction(ActionState.Running)
        viewModelScope.launch { run(pending.kind, accountId) }
    }

    private fun ask(kind: ActionKind, allowed: Boolean) {
        if (allowed && mutableState.value.action == ActionState.Idle) setAction(ActionState.Confirming(kind))
    }

    private suspend fun run(kind: ActionKind, accountId: String) {
        val build = mutableState.value.build
        val failure = when (kind) {
            ActionKind.Cancel -> (builds.cancel(accountId, build.buildUuid) as? ApiResult.Failure)?.error
            ActionKind.Retry -> (builds.retry(accountId, build) as? ApiResult.Failure)?.error
        }
        setAction(if (failure == null) ActionState.Done else ActionState.Failed(failure.toErrorKind()))
        if (failure == null) load()
    }

    /** Reads the build again, then the retry plan if the build has stopped. A failed read keeps what is shown. */
    private suspend fun load() {
        val accountId = activeAccountId() ?: return
        val listed = mutableState.value.build
        val fresh = (builds.detail(accountId, listed) as? ApiResult.Success)?.value ?: listed
        val plan = if (fresh.status == BuildStatus.STOPPED) {
            (builds.retryPlan(accountId, fresh) as? ApiResult.Success)?.value
        } else {
            null
        }
        mutableState.update { stateFor(fresh, plan).copy(action = it.action) }
    }

    /** The pinned account, only while the session is still connected to it. */
    private fun activeAccountId(): String? = pinnedAccountId?.takeIf { it == session.value.connectedAccountId() }

    private fun WatchSessionState.connectedAccountId(): String? = (this as? WatchSessionState.Connected)?.accountId

    private fun setAction(action: ActionState) = mutableState.update { it.copy(action = action) }

    private fun stateFor(build: BuildSummary, retryPlan: RetryPlan?) =
        DetailState(build = build, canCancel = build.status.isActive, retryPlan = retryPlan)
}
