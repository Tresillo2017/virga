package com.tomasps.virga.feature.r2.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.tomasps.virga.core.common.formatIsoDate
import com.tomasps.virga.feature.r2.R

/** A date the user can read, such as "Jan 10, 2026". A missing date shows [unknown], never a raw timestamp. */
internal fun r2DateLabel(raw: String?, unknown: String): String =
    formatIsoDate(raw?.takeIf { it.isNotBlank() }) ?: unknown

/** The name of an R2 storage class. Unknown classes are shown as the API sent them. */
@Composable
internal fun r2StorageClassLabel(storageClass: String?, unknown: String): String = when {
    storageClass.isNullOrBlank() -> unknown
    storageClass == "Standard" -> stringResource(R.string.r2_storage_standard)
    storageClass == "InfrequentAccess" -> stringResource(R.string.r2_storage_infrequent)
    else -> storageClass
}
