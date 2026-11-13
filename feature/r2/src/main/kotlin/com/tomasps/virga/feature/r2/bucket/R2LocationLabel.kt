package com.tomasps.virga.feature.r2.bucket

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.tomasps.virga.feature.r2.R

/** Friendly name for an R2 location hint code. Unknown codes are shown as given. */
@Composable
internal fun r2LocationLabel(hint: String): String = when (hint) {
    "enam" -> stringResource(R.string.r2_location_enam)
    "wnam" -> stringResource(R.string.r2_location_wnam)
    "weur" -> stringResource(R.string.r2_location_weur)
    "eeur" -> stringResource(R.string.r2_location_eeur)
    "apac" -> stringResource(R.string.r2_location_apac)
    "oc" -> stringResource(R.string.r2_location_oc)
    else -> hint
}
