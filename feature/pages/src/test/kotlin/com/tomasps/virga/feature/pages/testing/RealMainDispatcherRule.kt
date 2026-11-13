package com.tomasps.virga.feature.pages.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** Points Dispatchers.Main at the real default dispatcher, for tests that need real delays (polling) to elapse. */
@OptIn(ExperimentalCoroutinesApi::class)
class RealMainDispatcherRule : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(Dispatchers.Default)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
