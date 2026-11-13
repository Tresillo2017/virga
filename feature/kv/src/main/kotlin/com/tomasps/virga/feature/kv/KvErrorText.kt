package com.tomasps.virga.feature.kv

import com.tomasps.virga.core.common.result.ApiError
import com.tomasps.virga.feature.kv.R
import com.tomasps.virga.core.ui.state.ApiErrorUiMapper
import com.tomasps.virga.core.ui.state.UiText

/**
 * Message for a failed KV action. Only a single-key write gets the per-key 429 text, because the one write per second
 * per key limit is the cause there alone. A 429 on a namespace, bulk or other action gets the shared rate-limit text.
 */
fun kvActionMessage(error: ApiError, isSingleKeyWrite: Boolean): UiText = when {
    error is ApiError.RateLimited && isSingleKeyWrite -> UiText.Resource(R.string.kv_error_key_write_rate_limited)
    error is ApiError.NotFound -> UiText.Resource(R.string.kv_error_not_found)
    error is ApiError.Local -> UiText.Resource(R.string.kv_error_local)
    else -> ApiErrorUiMapper.toErrorUi(error).message
}
