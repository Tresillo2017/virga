package com.tomasps.virga.feature.workers.testing

import org.junit.Assert.assertTrue

/** Asserts that [value] is a [T], without a smart cast. Named like kotlin.test's helper so call sites read the same. */
inline fun <reified T> assertIs(value: Any?) {
    assertTrue("expected ${T::class.simpleName} but was ${value?.javaClass?.simpleName}", value is T)
}
