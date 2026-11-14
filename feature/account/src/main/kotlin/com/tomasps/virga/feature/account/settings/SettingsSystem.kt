package com.tomasps.virga.feature.account.settings

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.tomasps.virga.feature.account.about.appVersionName

/** What the device reports to the settings screen: whether app lock can work here, and the installed version. */
data class DeviceFacts(
    val isDeviceSecure: Boolean,
    val versionName: String,
)

fun deviceFacts(context: Context): DeviceFacts = DeviceFacts(
    isDeviceSecure = isDeviceSecure(context),
    versionName = appVersionName(context),
)

/** True when the device has a screen lock or biometrics, the only thing app lock can verify against. */
fun isDeviceSecure(context: Context): Boolean =
    context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

/** Opens the system notification settings for this app. */
fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
