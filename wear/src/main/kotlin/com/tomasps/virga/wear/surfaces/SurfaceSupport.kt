package com.tomasps.virga.wear.surfaces

import android.util.Log
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import com.tomasps.virga.wear.MainActivity
import com.tomasps.virga.wear.ui.EXTRA_START_ROUTE
import kotlinx.coroutines.CancellationException

private const val TAG = "VirgaSurface"

/** [BuildSurfaceRefresher.refresh] that never throws: a storage failure is logged by class name and reads as no data. */
internal suspend fun BuildSurfaceRefresher.refreshOrNull(): SurfaceData? = try {
    refresh()
} catch (error: CancellationException) {
    throw error
} catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
    Log.e(TAG, "Refresh failed: ${error::class.java.name}")
    null
}

internal const val OPEN_APP_CLICK_ID = "open_app"

/** A text line for a tile layout. */
internal fun tileText(value: String, sizeSp: Float, color: Int): LayoutElementBuilders.LayoutElement =
    LayoutElementBuilders.Text.Builder()
        .setText(value)
        .setMaxLines(1)
        .setFontStyle(
            LayoutElementBuilders.FontStyle.Builder().setSize(sp(sizeSp)).setColor(argb(color)).build(),
        )
        .build()

/** Tapping the element opens [MainActivity], on [route] when given, otherwise on its home (the build list). */
internal fun openAppModifiers(packageName: String, route: String?): ModifiersBuilders.Modifiers {
    val activity = ActionBuilders.AndroidActivity.Builder()
        .setPackageName(packageName)
        .setClassName(MainActivity::class.java.name)
    if (route != null) {
        activity.addKeyToExtraMapping(EXTRA_START_ROUTE, ActionBuilders.AndroidStringExtra.Builder().setValue(route).build())
    }
    val click = ModifiersBuilders.Clickable.Builder()
        .setId(OPEN_APP_CLICK_ID)
        .setOnClick(ActionBuilders.LaunchAction.Builder().setAndroidActivity(activity.build()).build())
        .build()
    return ModifiersBuilders.Modifiers.Builder().setClickable(click).build()
}
