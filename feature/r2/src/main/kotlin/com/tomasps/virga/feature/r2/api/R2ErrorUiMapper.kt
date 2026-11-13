package com.tomasps.virga.feature.r2.api

import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.core.ui.state.ApiErrorUiMapper
import com.tomasps.virga.core.ui.state.ErrorUi
import com.tomasps.virga.core.ui.state.ErrorUiMapper
import com.tomasps.virga.core.ui.state.UiText
import com.tomasps.virga.feature.r2.R

/** Cloudflare code for "not authorised: the token needs Admin Read and Write (all buckets)". */
const val R2_CODE_ADMIN_ALL_BUCKETS = 10002

/** Cloudflare code for "not authorised: the token is limited to one bucket and this call needs more". */
const val R2_CODE_BUCKET_SCOPED = 10000

/**
 * R2 errors with the permission explained. Object calls over REST need an Admin token, so a 401 or 403 with
 * one of the codes above says which permission is missing. Everything else uses the shared mapping.
 */
object R2ErrorUiMapper : ErrorUiMapper<ApiError> {

    override fun toErrorUi(error: ApiError): ErrorUi {
        val isAuthFailure = error is ApiError.Unauthorized || error is ApiError.Forbidden
        return when {
            isAuthFailure && error.codes.contains(R2_CODE_ADMIN_ALL_BUCKETS) -> ErrorUi(
                title = UiText.Resource(R.string.r2_error_admin_all_title),
                message = UiText.Resource(R.string.r2_error_admin_all_message),
                retryable = false,
            )
            isAuthFailure && error.codes.contains(R2_CODE_BUCKET_SCOPED) -> ErrorUi(
                title = UiText.Resource(R.string.r2_error_admin_bucket_title),
                message = UiText.Resource(R.string.r2_error_admin_bucket_message),
                retryable = false,
            )
            else -> ApiErrorUiMapper.toErrorUi(error)
        }
    }
}
