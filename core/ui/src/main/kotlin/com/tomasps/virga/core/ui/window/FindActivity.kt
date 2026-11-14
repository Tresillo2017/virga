package com.tomasps.virga.core.ui.window

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** The activity that hosts this context, found by unwrapping [ContextWrapper]s. Null when no activity is in the chain. */
fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
