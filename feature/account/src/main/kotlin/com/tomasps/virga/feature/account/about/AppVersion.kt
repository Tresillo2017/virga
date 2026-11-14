package com.tomasps.virga.feature.account.about

import android.content.Context

private const val UNKNOWN_VERSION = "?"

/** The installed app's version name, or a placeholder when the package manager cannot report it. */
fun appVersionName(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
        .getOrNull() ?: UNKNOWN_VERSION
