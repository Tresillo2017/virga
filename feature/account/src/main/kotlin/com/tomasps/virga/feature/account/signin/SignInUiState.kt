package com.tomasps.virga.feature.account.signin

import com.tomasps.virga.core.ui.state.ErrorUi

data class SignInUiState(
    val token: String = "",
    val isTokenVisible: Boolean = false,
    val isVerifying: Boolean = false,
    val error: ErrorUi? = null,
    val isGuidanceExpanded: Boolean = false,
) {
    /** The token is redacted here, as in Credential.ApiToken, so no debugger, test or log line can print it. */
    override fun toString(): String =
        "SignInUiState(token=***, isTokenVisible=$isTokenVisible, isVerifying=$isVerifying, " +
            "error=$error, isGuidanceExpanded=$isGuidanceExpanded)"
}
