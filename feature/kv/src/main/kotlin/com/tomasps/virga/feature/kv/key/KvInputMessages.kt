package com.tomasps.virga.feature.kv.key

import com.tomasps.virga.core.ui.state.UiText
import com.tomasps.virga.feature.kv.R
import com.tomasps.virga.feature.kv.value.ExpirationInputProblem
import com.tomasps.virga.feature.kv.value.KvExpirationProblem
import com.tomasps.virga.feature.kv.value.MetadataProblem

/** Text for a metadata problem the editor found before anything was sent. */
internal fun metadataProblemText(problem: MetadataProblem): UiText = when (problem) {
    MetadataProblem.INVALID_JSON -> UiText.Resource(R.string.kv_error_metadata_invalid)
    MetadataProblem.TOO_LARGE -> UiText.Resource(R.string.kv_error_metadata_too_large)
}

/** Text for an expiration the editor could not read. */
internal fun expirationInputText(problem: ExpirationInputProblem): UiText = when (problem) {
    ExpirationInputProblem.NOT_A_NUMBER -> UiText.Resource(R.string.kv_error_expiration_not_number)
    ExpirationInputProblem.NOT_A_DATE_TIME -> UiText.Resource(R.string.kv_error_expiration_not_date)
}

/** Text for an expiration that breaks the 60 second rule. */
internal fun expirationProblemText(problem: KvExpirationProblem): UiText = when (problem) {
    KvExpirationProblem.TTL_TOO_SHORT -> UiText.Resource(R.string.kv_error_expiration_ttl_short)
    KvExpirationProblem.ABSOLUTE_TOO_SOON -> UiText.Resource(R.string.kv_error_expiration_too_soon)
}
