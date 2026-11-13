package com.tomasps.virga.feature.kv

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Applies [change] only when [canStart] holds for the current value, as one atomic step. Returns true when the change
 * was applied. A second caller that finds the state already busy gets false and must not start its work.
 */
internal fun <T> MutableStateFlow<T>.tryUpdate(canStart: (T) -> Boolean, change: (T) -> T): Boolean {
    while (true) {
        val current = value
        if (!canStart(current)) return false
        if (compareAndSet(current, change(current))) return true
    }
}
