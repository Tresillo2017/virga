package com.tomasps.virga

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import com.tomasps.virga.core.auth.session.SessionRepository
import com.tomasps.virga.core.navigation.DeepLinks
import com.tomasps.virga.core.navigation.PendingNavigation
import com.tomasps.virga.feature.account.settings.ThemeRepository
import com.tomasps.virga.shell.VirgaRoot
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.android.ext.android.inject

/** FragmentActivity, because the app lock prompt (androidx.biometric) needs one. */
class MainActivity : FragmentActivity() {

    private val session: SessionRepository by inject()
    private val themes: ThemeRepository by inject()
    private val pending: PendingNavigation by inject()

    /** Set once the Compose splash has drawn its first frame; the system splash stays up until then. */
    private val composeSplashStarted = MutableStateFlow(false)

    /** Set when the system splash has handed over, so the Compose splash starts its clock on a visible screen. */
    private val composeSplashVisible = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        val systemSplash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // A recreated activity (rotation, font scale, theme) does not replay the Compose splash, which VirgaRoot
        // remembers as finished. Without this, the system splash would wait for a callback that never comes.
        // The system splash is not shown again, so the Compose splash never waits for its hand-off after a recreation.
        if (savedInstanceState != null) {
            composeSplashStarted.value = true
            composeSplashVisible.value = true
        }
        enableEdgeToEdge()
        // Released once the Compose splash is on screen. The Compose splash itself holds until the session is restored.
        systemSplash.setKeepOnScreenCondition { !composeSplashStarted.value }
        systemSplash.setOnExitAnimationListener { provider ->
            composeSplashVisible.value = true
            provider.remove()
        }
        setContent {
            VirgaRoot(
                sessionProvider = { session },
                themesProvider = { themes },
                pending = pending,
                splashVisible = composeSplashVisible,
                onSplashStarted = { composeSplashStarted.value = true },
            )
        }
        // A recreated activity still holds its original intent, so only a fresh launch is read here.
        if (savedInstanceState == null) offerDeepLink(intent)
    }

    /** singleTop: a notification tapped while the app is open arrives here, not in onCreate. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        offerDeepLink(intent)
    }

    private fun offerDeepLink(intent: Intent?) {
        DeepLinks.parse(intent?.dataString)?.let(pending::offer)
    }
}
