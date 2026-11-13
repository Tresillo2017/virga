package com.tomasps.virga.core.testing

import androidx.navigation3.runtime.NavKey

/**
 * Wraps the live back stack and remembers every key that was pushed onto it. [Navigator] is a final class, so the
 * recording happens here: every `navigate` and every `replaceAll` goes through [add]. Removals are not recorded.
 */
internal class RecordingBackStack(private val live: MutableList<NavKey>) : MutableList<NavKey> by live {

    private val pushed = mutableListOf<NavKey>()

    /** Every key pushed since the harness started, in order. The initial stack is not included. */
    val recorded: List<NavKey> get() = pushed.toList()

    override fun add(element: NavKey): Boolean {
        pushed += element
        return live.add(element)
    }

    override fun add(index: Int, element: NavKey) {
        pushed += element
        live.add(index, element)
    }
}
