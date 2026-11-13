package com.tomasps.virga.feature.account.signin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomasps.virga.core.auth.session.SessionRepository
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.ui.state.ApiErrorUiMapper
import com.tomasps.virga.core.ui.state.ErrorUi
import com.tomasps.virga.core.ui.state.UiText
import com.tomasps.virga.feature.account.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SignInViewModel(private val session: SessionRepository) : ViewModel() {

    private val mutableState = MutableStateFlow(SignInUiState())
    val state: StateFlow<SignInUiState> = mutableState.asStateFlow()

    fun onTokenChanged(value: String) {
        mutableState.update { it.copy(token = value, error = null) }
    }

    fun onToggleTokenVisibility() {
        mutableState.update { it.copy(isTokenVisible = !it.isTokenVisible) }
    }

    fun onGuidanceToggled() {
        mutableState.update { it.copy(isGuidanceExpanded = !it.isGuidanceExpanded) }
    }

    /** Verifies the typed token. On success the field is cleared and the session moves the app forward. */
    fun onSignIn() {
        val token = mutableState.value.token.trim()
        if (token.isEmpty()) {
            mutableState.update { it.copy(error = emptyTokenError()) }
            return
        }
        if (mutableState.value.isVerifying) return
        viewModelScope.launch {
            mutableState.update { it.copy(isVerifying = true, error = null) }
            val result = session.signInWithToken(token)
            mutableState.update { current -> current.afterAttempt(result) }
        }
    }

    fun onExploreDemo() {
        if (mutableState.value.isVerifying) return
        viewModelScope.launch {
            mutableState.update { it.copy(isVerifying = true, error = null) }
            val result = session.enterDemo()
            mutableState.update { current -> current.afterAttempt(result) }
        }
    }

    private fun SignInUiState.afterAttempt(result: ApiResult<Unit>): SignInUiState = when (result) {
        is ApiResult.Success -> copy(token = "", isVerifying = false, error = null)
        is ApiResult.Failure -> copy(isVerifying = false, error = ApiErrorUiMapper.toErrorUi(result.error))
    }

    private fun emptyTokenError() = ErrorUi(
        title = UiText.Resource(R.string.account_sign_in_empty_title),
        message = UiText.Resource(R.string.account_sign_in_empty_message),
        retryable = false,
    )
}
