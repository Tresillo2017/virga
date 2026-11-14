package com.tomasps.virga.shell

import android.os.Build
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.tomasps.virga.R

/**
 * Starts the system unlock prompt on [activity]: a strong biometric, or the device's screen lock as a fallback. [onSuccess]
 * runs only when the user is verified. A cancelled or failed attempt leaves the lock screen up.
 */
fun showUnlockPrompt(activity: FragmentActivity, onSuccess: () -> Unit) {
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            onSuccess()
        }

        // A cancel or a lockout leaves the lock screen up with its Unlock button. A missing screen lock never reaches
        // here: app lock only engages while the device is secure (see rememberDeviceSecure).
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            Log.i(LOG_TAG, "Unlock prompt ended without verification (code $errorCode)")
        }
    }
    BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
        .authenticate(unlockPromptInfo(activity))
}

private const val LOG_TAG = "VirgaAppLock"

private fun unlockPromptInfo(activity: FragmentActivity): BiometricPrompt.PromptInfo {
    val builder = BiometricPrompt.PromptInfo.Builder().setTitle(activity.getString(R.string.app_lock_prompt_title))
    // Android 11 and newer take one combined authenticator mask. Older releases take the device credential as a separate
    // flag, which cannot be combined with an authenticator mask, so only the flag is set there. Neither path sets a
    // negative button: the device credential option needs the system to provide the cancel control.
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()
    } else {
        builder.setDeviceCredentialAllowed(true).build()
    }
}
