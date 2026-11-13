package com.tomasps.virga.core.ui.state

import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.ui.R

/** The shared mapping from transport errors to friendly text. Raw API messages are never shown. */
object ApiErrorUiMapper : ErrorUiMapper<ApiError> {

    override fun toErrorUi(error: ApiError): ErrorUi = when (error) {
        is ApiError.Unauthorized -> ui(R.string.core_ui_error_unauthorized_title, R.string.core_ui_error_unauthorized_message, retryable = false)
        is ApiError.Forbidden -> ui(R.string.core_ui_error_forbidden_title, R.string.core_ui_error_forbidden_message, retryable = false)
        is ApiError.NotFound -> ui(R.string.core_ui_error_not_found_title, R.string.core_ui_error_not_found_message, retryable = false)
        is ApiError.RateLimited -> ui(R.string.core_ui_error_rate_limited_title, R.string.core_ui_error_rate_limited_message, retryable = true)
        is ApiError.Rejected -> ui(R.string.core_ui_error_rejected_title, R.string.core_ui_error_rejected_message, retryable = false)
        is ApiError.GraphQl -> ui(R.string.core_ui_error_graphql_title, R.string.core_ui_error_graphql_message, retryable = true)
        is ApiError.Server -> ui(R.string.core_ui_error_server_title, R.string.core_ui_error_server_message, retryable = true)
        is ApiError.Network -> ui(R.string.core_ui_error_network_title, R.string.core_ui_error_network_message, retryable = true)
        is ApiError.Decode -> ui(R.string.core_ui_error_decode_title, R.string.core_ui_error_decode_message, retryable = true)
        is ApiError.Local -> ui(R.string.core_ui_error_local_title, R.string.core_ui_error_local_message, retryable = true)
    }

    private fun ui(titleRes: Int, messageRes: Int, retryable: Boolean) = ErrorUi(
        title = UiText.Resource(titleRes),
        message = UiText.Resource(messageRes),
        retryable = retryable,
    )
}
