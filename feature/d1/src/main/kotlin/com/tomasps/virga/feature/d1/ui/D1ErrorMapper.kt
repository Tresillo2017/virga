package com.tomasps.virga.feature.d1.ui

import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.ui.state.ApiErrorUiMapper
import com.tomasps.virga.core.ui.state.ErrorUi
import com.tomasps.virga.core.ui.state.ErrorUiMapper
import com.tomasps.virga.core.ui.state.UiText
import com.tomasps.virga.feature.d1.R
import com.tomasps.virga.feature.d1.data.D1Limits
import com.tomasps.virga.feature.d1.data.ExportRepository

/**
 * D1's own failures first (the 30-second limit, the 50 MB result cap, an export that ran out of time), then the
 * shared mapping. The API's message text is never shown; these cases get their own strings.
 */
object D1ErrorMapper : ErrorUiMapper<ApiError> {

    val limitTitle: UiText = UiText.Resource(R.string.d1_error_limit_title)
    val resultTooLargeTitle: UiText = UiText.Resource(R.string.d1_error_result_too_large_title)
    val exportTimeoutTitle: UiText = UiText.Resource(R.string.d1_error_export_timeout_title)
    val writeUnconfirmedTitle: UiText = UiText.Resource(R.string.d1_error_write_unconfirmed_title)

    /**
     * For a statement that may write. The 30-second limit cancels the client's wait, not the statement, so a timed-out
     * write may still have been applied. Retrying it blind could repeat it, so this case is not offered as a retry.
     */
    fun toStatementErrorUi(error: ApiError, mayWrite: Boolean): ErrorUi = when {
        mayWrite && error is ApiError.Local && error.reason == D1Limits.REQUEST_LIMIT_REASON -> ErrorUi(
            title = writeUnconfirmedTitle,
            message = UiText.Resource(R.string.d1_error_write_unconfirmed_message),
            retryable = false,
        )
        else -> toErrorUi(error)
    }

    override fun toErrorUi(error: ApiError): ErrorUi = when {
        error is ApiError.Local && error.reason == D1Limits.REQUEST_LIMIT_REASON -> ErrorUi(
            title = limitTitle,
            message = UiText.Resource(R.string.d1_error_limit_message),
            retryable = true,
        )
        error is ApiError.Local && error.reason == ExportRepository.TIMED_OUT_REASON -> ErrorUi(
            title = exportTimeoutTitle,
            message = UiText.Resource(R.string.d1_error_export_timeout_message),
            retryable = true,
        )
        error.messages.any { it.message.orEmpty().contains(D1Limits.RESULT_TOO_LARGE_MARKER) } -> ErrorUi(
            title = resultTooLargeTitle,
            message = UiText.Resource(R.string.d1_error_result_too_large_message),
            retryable = false,
        )
        else -> ApiErrorUiMapper.toErrorUi(error)
    }
}
