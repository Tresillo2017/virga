package com.tomasps.virga.core.auth.vault

import com.tomasps.virga.core.auth.Credential
import com.tomasps.virga.core.auth.SessionHarness
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CredentialVaultTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `an empty vault reads as no credential`() = runBlocking {
        // Arrange
        val vault = SessionHarness(folder).vault

        // Act and Assert
        assertNull(vault.read())
    }

    @Test
    fun `an api token written to the vault is read back intact`() = runBlocking {
        // Arrange
        val vault = SessionHarness(folder).vault

        // Act
        vault.write(Credential.ApiToken("cf-token-123"))

        // Assert
        assertEquals(Credential.ApiToken("cf-token-123"), vault.read())
    }

    @Test
    fun `the demo credential round trips through the vault`() = runBlocking {
        // Arrange
        val vault = SessionHarness(folder).vault

        // Act
        vault.write(Credential.Demo)

        // Assert
        assertEquals(Credential.Demo, vault.read())
    }

    @Test
    fun `wipe removes the credential so the next read is empty`() = runBlocking {
        // Arrange
        val vault = SessionHarness(folder).vault
        vault.write(Credential.ApiToken("cf-token-123"))

        // Act
        vault.wipe()

        // Assert
        assertNull(vault.read())
    }

    @Test
    fun `the token never appears in the preferences file in plain text`() = runBlocking {
        // Arrange
        val harness = SessionHarness(folder)
        val token = "cf-token-plaintext-canary"

        // Act
        harness.vault.write(Credential.ApiToken(token))
        harness.store.updateData { it }
        val onDisk = folder.root.walkTopDown().filter(File::isFile).joinToString("\n") { it.readBytes().decodeToString() }

        // Assert
        assertFalse(onDisk.contains(token))
    }

    @Test
    fun `a ciphertext that no longer decrypts reads as no credential instead of throwing`() = runBlocking {
        // Arrange
        val harness = SessionHarness(folder)
        harness.vault.write(Credential.ApiToken("cf-token-123"))
        val corrupting = CredentialVault(harness.store, AesGcmCipher { javax.crypto.KeyGenerator.getInstance("AES").apply { init(256) }.generateKey() })

        // Act and Assert
        assertNull(corrupting.read())
    }
}
