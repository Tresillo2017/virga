package com.tomasps.virga.wearsync

import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearApiAvailabilityTest {

    private fun apiError(code: Int) = ApiException(Status(code))

    @Test
    fun apiNotConnectedIsUnavailable() {
        assertTrue(isWearApiUnavailable(apiError(CommonStatusCodes.API_NOT_CONNECTED)))
    }

    @Test
    fun canceledIsATransientErrorNotUnavailable() {
        assertFalse(isWearApiUnavailable(apiError(CommonStatusCodes.CANCELED)))
    }

    @Test
    fun otherExceptionsAreNotUnavailable() {
        assertFalse(isWearApiUnavailable(IllegalStateException("boom")))
    }
}
