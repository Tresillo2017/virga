package com.tomasps.virga.feature.builds.testing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Routes Dispatchers.Main (used by viewModelScope) to a virtual-time test dispatcher. Use [runVmTest] for ViewModel
 * tests and [keep] to create the ViewModels in them. A ViewModel's polling then advances on virtual time and never
 * sleeps, and [runVmTest] clears every ViewModel before it returns, so no loop outlives its test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule : TestWatcher() {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private var keyCount = 0

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        store.clear()
        Dispatchers.resetMain()
    }

    /** Creates a ViewModel that this rule owns, so [runVmTest] can cancel its coroutines. */
    fun <T : ViewModel> keep(modelClass: Class<T>, create: () -> T): T {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <V : ViewModel> create(modelClass: Class<V>): V = create() as V
        }
        return ViewModelProvider.create(store, factory)["vm-${keyCount++}", modelClass]
    }

    /** Runs [block] on virtual time, then clears the rule's ViewModels before runTest drains the scheduler. */
    fun runVmTest(block: suspend TestScope.() -> Unit) = runTest {
        try {
            block()
        } finally {
            store.clear()
        }
    }
}
