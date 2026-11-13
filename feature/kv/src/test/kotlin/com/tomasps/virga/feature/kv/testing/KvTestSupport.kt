package com.tomasps.virga.feature.kv.testing

import com.tomasps.virga.core.auth.session.CloudflareAccount
import com.tomasps.virga.core.auth.session.SessionRepository
import com.tomasps.virga.core.auth.session.SessionState
import com.tomasps.virga.core.common.result.ApiResult
import com.tomasps.virga.core.common.DispatcherProvider
import com.tomasps.virga.feature.kv.files.KvFileStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeout

/** Fixed clock for expiration tests, in epoch seconds. */
const val TEST_NOW_EPOCH_SECONDS = 1_800_000_000L

/** Upper bound for waiting on a ViewModel state, in milliseconds. */
const val WAIT_MS = 2_000L

/** How often [awaitTrue] re-checks its condition, in milliseconds. */
private const val POLL_MS = 5L

/**
 * Waits, in real time, until [predicate] holds. The ViewModels run their network calls on other threads, so a test
 * waits for the end state instead of assuming the call has finished. Fails after [WAIT_MS].
 */
suspend fun awaitTrue(predicate: () -> Boolean) {
    withTimeout(WAIT_MS) {
        while (!predicate()) delay(POLL_MS)
    }
}

/** A signed-in demo session with one selected account. */
fun signedInSession(accountId: String): SessionRepository = object : SessionRepository {
    private val mutableState = MutableStateFlow<SessionState>(
        SessionState.SignedIn(
            accounts = listOf(CloudflareAccount(id = accountId, name = "Test")),
            selectedAccountId = accountId,
            isDemo = true,
        ),
    )
    override val state: StateFlow<SessionState> = mutableState.asStateFlow()
    override suspend fun restore() = Unit
    override suspend fun signInWithToken(token: String): ApiResult<Unit> = ApiResult.Success(Unit)
    override suspend fun enterDemo(): ApiResult<Unit> = ApiResult.Success(Unit)
    override suspend fun selectAccount(accountId: String) = Unit
    override suspend fun signOut() = Unit
}

/** In-memory document store: sizes and bytes come from a map keyed by URI string. */
class FakeKvFileStore : KvFileStore {
    val documents = mutableMapOf<String, ByteArray>()
    val written = mutableMapOf<String, ByteArray>()

    override suspend fun sizeOf(uri: String): Long? = documents[uri]?.size?.toLong()

    override suspend fun displayNameOf(uri: String): String? = uri.substringAfterLast('/')

    override suspend fun readBytes(uri: String, maxBytes: Long): ByteArray? =
        documents[uri]?.takeIf { it.size <= maxBytes }

    override suspend fun writeBytes(uri: String, bytes: ByteArray): Boolean {
        written[uri] = bytes
        return true
    }
}

/** Every dispatcher the import view model uses is [dispatcher], so one test scheduler drives all of its steps. */
fun testDispatchers(dispatcher: CoroutineDispatcher): DispatcherProvider = object : DispatcherProvider {
    override val io: CoroutineDispatcher = dispatcher
    override val default: CoroutineDispatcher = dispatcher
}
