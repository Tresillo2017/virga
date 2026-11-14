package com.tomasps.virga.wearsync

import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes

private val UNAVAILABLE_STATUS_CODES = setOf(
    ConnectionResult.API_UNAVAILABLE,
    CommonStatusCodes.API_NOT_CONNECTED,
    ConnectionResult.SERVICE_MISSING,
    ConnectionResult.SERVICE_DISABLED,
    ConnectionResult.SERVICE_INVALID,
)

/** True when the Wearable API cannot work on this phone at all (no Play Services or no Wear OS app), so retries are pointless. */
fun isWearApiUnavailable(error: Exception): Boolean =
    error is ApiException && error.statusCode in UNAVAILABLE_STATUS_CODES
