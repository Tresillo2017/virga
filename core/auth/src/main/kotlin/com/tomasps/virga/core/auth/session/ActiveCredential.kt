package com.tomasps.virga.core.auth.session

import com.tomasps.virga.core.network.CredentialProvider
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The token every request uses. Sign-in sets it before verifying, so the verify call goes out with the
 * candidate token that is not yet in the vault. A null value makes every live request fail before I/O.
 */
class ActiveCredential : CredentialProvider {
    private val token = MutableStateFlow<String?>(null)

    override suspend fun bearerToken(): String? = token.value

    fun use(newToken: String?) {
        token.value = newToken
    }
}
