package com.tomasps.virga.wearsync

import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes

// CommonStatusCodes only: ApiException.statusCode is in this namespace (ConnectionResult codes overlap, e.g. 16 is
// CANCELED here, a transient error).
private val UNAVAILABLE_STATUS_CODES = setOf(
    CommonStatusCodes.API_NOT_CONNECTED,
)

/** True when the Wearable API cannot work on this phone (no Wear OS app), so background retries are pointless. */
fun isWearApiUnavailable(error: Exception): Boolean =
    error is ApiException && error.statusCode in UNAVAILABLE_STATUS_CODES
