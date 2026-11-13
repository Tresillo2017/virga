package com.tomasps.virga

import android.app.Application
import android.content.Context
import org.junit.Test
import org.koin.dsl.module
import org.koin.test.verify.verify

/**
 * Checks that every definition in the app's Koin graph can be resolved: each constructor parameter has a binding, and
 * each binding's declared type is the one a consumer asks for. Runs on the JVM with no device, so a missing or
 * mistyped binding fails the build instead of crashing the first screen that needs it.
 */
class KoinGraphTest {

    @Test
    fun `every Koin definition resolves`() {
        val wholeGraph = module { includes(virgaKoinModules) }
        // Function0 covers inline clocks such as WatchRegistry's `() -> Long`, which each definition passes as a lambda.
        wholeGraph.verify(extraTypes = listOf(Context::class, Application::class, Function0::class))
    }
}
