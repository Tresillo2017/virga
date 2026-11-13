package com.tomasps.virga.core.ui.state

import androidx.compose.runtime.Immutable
import com.tomasps.virga.core.ui.R

/**
 * What the UI shows for a failed request. Built from the domain error by an [ErrorUiMapper], so the
 * UI never switches on transport details.
 */
@Immutable
data class ErrorUi(
    val title: UiText,
    val message: UiText,
    val retryable: Boolean,
) {
    companion object {
        /** Fallback for errors nobody mapped. Retryable, because most unknown failures are transient. */
        val Generic = ErrorUi(
            title = UiText.Resource(R.string.core_ui_error_title_default),
            message = UiText.Resource(R.string.core_ui_error_message_default),
            retryable = true,
        )
    }
}

/**
 * Mapper slot for the shell to connect a domain error type (for example `ApiError`) to [ErrorUi].
 * A plain function, so ViewModels can call it without a Context.
 */
fun interface ErrorUiMapper<in E> {
    fun toErrorUi(error: E): ErrorUi
}
