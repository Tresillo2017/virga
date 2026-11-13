package com.tomasps.virga.core.network

/**
 * Receives one line per completed request: method, path (no query) and status. Headers and bodies are
 * never passed here, so the Authorization header cannot reach a log.
 */
fun interface ApiLogger {
    fun log(line: String)

    companion object {
        val None: ApiLogger = ApiLogger { }
    }
}
