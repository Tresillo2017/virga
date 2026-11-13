package com.tomasps.virga.feature.builds.ui

import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.ui.state.ApiErrorUiMapper
import com.tomasps.virga.core.ui.state.ErrorUi
import com.tomasps.virga.core.ui.state.UiText
import com.tomasps.virga.feature.builds.R

/**
 * The error shown for a builds call. The Builds API accepts only user API tokens, so an authorization failure here
 * is most often an account token. That case gets a hint that names the cause; other errors use the shared mapping.
 */
internal fun buildsErrorUi(error: ApiError): ErrorUi {
    val shared = ApiErrorUiMapper.toErrorUi(error)
    return when (error) {
        is ApiError.Unauthorized, is ApiError.Forbidden -> shared.copy(
            message = UiText.Resource(R.string.builds_error_user_token),
        )
        else -> shared
    }
}
