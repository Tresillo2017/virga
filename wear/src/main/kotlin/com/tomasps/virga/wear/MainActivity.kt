package com.tomasps.virga.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.tomasps.virga.wear.ui.EXTRA_START_ROUTE
import com.tomasps.virga.wear.ui.WearApp
import com.tomasps.virga.wear.ui.startRouteFrom

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A tile tap names the build to open. Only a fresh launch honours it, so a rotation or recreation does not reopen it.
        val startRoute = if (savedInstanceState == null) startRouteFrom(intent.getStringExtra(EXTRA_START_ROUTE)) else null
        setContent { WearApp(startRoute = startRoute) }
    }

    override fun onStart() {
        super.onStart()
        (application as? VirgaWearApp)?.onAppOpened()
    }
}
