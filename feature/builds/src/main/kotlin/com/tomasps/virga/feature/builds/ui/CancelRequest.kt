package com.tomasps.virga.feature.builds.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** True when the launching intent's extra names [buildUuid], so the screen should ask to confirm a cancel. */
internal fun isCancelRequestFor(extra: String?, buildUuid: String): Boolean = !extra.isNullOrEmpty() && extra == buildUuid

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
