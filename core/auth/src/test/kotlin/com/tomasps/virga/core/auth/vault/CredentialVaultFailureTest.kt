package com.tomasps.virga.core.auth.vault

import com.tomasps.virga.core.auth.Credential
import com.tomasps.virga.core.auth.SessionHarness
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.ProviderException

class CredentialVaultFailureTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val throwingCipher = object : SecretCipher {
        override fun encrypt(plaintext: ByteArray): SealedBytes = throw ProviderException("no key")
        override fun decrypt(sealed: SealedBytes): ByteArray = throw ProviderException("no key")
    }

    @Test
    fun `a keystore runtime exception on write returns false instead of throwing`() = runBlocking {
        val vault = CredentialVault(SessionHarness(folder).store, throwingCipher)

        assertFalse(vault.write(Credential.ApiToken("cf-token")))
    }

    @Test
    fun `a keystore runtime exception on read reads as no credential`() = runBlocking {
        val harness = SessionHarness(folder)
        assertTrue(harness.vault.write(Credential.ApiToken("cf-token")))

        val vault = CredentialVault(harness.store, throwingCipher)

        assertNull(vault.read())
    }

    @Test
    fun `a successful write reports true`() = runBlocking {
        assertTrue(SessionHarness(folder).vault.write(Credential.Demo))
    }
}
