package com.tomasps.virga.core.common

import app.cash.turbine.test
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Proves JUnit, kotlinx-coroutines-test and Turbine resolve and run in a unit test. */
class TestToolingSmokeTest {

    @Test
    fun turbineCollectsFlowEmittedUnderRunTest() = runTest {
        // Arrange
        val source = flowOf("virga")

        // Act and Assert
        source.test {
            assertEquals("virga", awaitItem())
            awaitComplete()
        }
    }
}
