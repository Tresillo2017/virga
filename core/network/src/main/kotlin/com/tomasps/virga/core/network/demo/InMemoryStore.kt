package com.tomasps.virga.core.network.demo

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update

/**
 * Tiny keyed store for demo writes, so a created or deleted demo object shows up on the next read.
 * Every change swaps in a new map, so observers never see a mutated collection.
 */
class InMemoryStore<V>(initial: Map<String, V> = emptyMap()) {
    private val mutableItems = MutableStateFlow(initial)

    val items: StateFlow<Map<String, V>> = mutableItems.asStateFlow()

    fun get(key: String): V? = mutableItems.value[key]

    fun put(key: String, value: V) {
        mutableItems.update { it + (key to value) }
    }

    /** Removes [key] and returns the value it held, or null when it was absent. */
    fun remove(key: String): V? = mutableItems.getAndUpdate { it - key }[key]
}
