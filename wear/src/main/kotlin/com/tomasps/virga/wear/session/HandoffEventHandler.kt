package com.tomasps.virga.wear.session

import kotlinx.coroutines.CancellationException

sealed interface HandoffEvent {
    /** The phone published or updated the credential item; [bytes] is its raw body. */
    class Changed(val bytes: ByteArray) : HandoffEvent

    /** The phone deleted the credential item (sign-out). */
    data object Deleted : HandoffEvent
}

/**
 * Applies Data Layer events strictly in the order given, which is arrival order. The phone's clock is deliberately not
 * used to order them. A failing event is reported through [onError] (callers must log the class only: payloads carry
 * the token) and does not stop later events, so a sign-out that follows a failed update is still honoured.
 */
class HandoffEventHandler(
    private val repository: WatchSessionRepository,
    private val onError: (Throwable) -> Unit,
) {
    suspend fun handle(events: List<HandoffEvent>) {
        for (event in events) {
            try {
                when (event) {
                    is HandoffEvent.Changed -> repository.applyEncoded(event.bytes)
                    HandoffEvent.Deleted -> repository.clear()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                onError(error)
            }
        }
    }
}
