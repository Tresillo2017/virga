package com.tomasps.virga.core.auth.vault

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.tomasps.virga.core.auth.Credential
import com.tomasps.virga.core.common.DispatcherProvider
import com.tomasps.virga.core.common.StandardDispatcherProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.Base64

/**
 * Keeps the one credential in DataStore as IV + ciphertext. The plaintext JSON exists only in memory for
 * the duration of a call. Keystore and DataStore work runs on the IO dispatcher, never on the main thread.
 *
 * [read] returns null when nothing is stored or the stored bytes cannot be read on this device (a lost
 * Keystore key, a tampered file, a DataStore I/O error). [write] returns false when the credential could not be
 * saved. Neither throws, except cancellation.
 */
class CredentialVault(
    private val store: DataStore<Preferences>,
    private val cipher: SecretCipher,
    private val dispatchers: DispatcherProvider = StandardDispatcherProvider(),
) {

    suspend fun read(): Credential? = withContext(dispatchers.io) {
        try {
            val prefs = store.data.first()
            val ivText = prefs[IV_KEY] ?: return@withContext null
            val ciphertextText = prefs[CIPHERTEXT_KEY] ?: return@withContext null
            decryptCredential(SealedBytes(decode(ivText), decode(ciphertextText)))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // Keystore errors (ProviderException, GeneralSecurityException), I/O errors and malformed
            // stored data all mean the same thing to the session: no usable credential on this device.
            null
        }
    }

    /** Encrypts and stores [credential]. Returns false, and leaves the previous value in place, on any failure. */
    suspend fun write(credential: Credential): Boolean = withContext(dispatchers.io) {
        try {
            val plain = Json.encodeToString(Credential.serializer(), credential).encodeToByteArray()
            val sealed = cipher.encrypt(plain)
            store.edit { prefs ->
                prefs[IV_KEY] = encode(sealed.iv)
                prefs[CIPHERTEXT_KEY] = encode(sealed.ciphertext)
            }
            true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            false
        }
    }

    suspend fun wipe() {
        withContext(dispatchers.io) {
            store.edit { prefs ->
                prefs.remove(IV_KEY)
                prefs.remove(CIPHERTEXT_KEY)
            }
        }
    }

    private fun decryptCredential(sealed: SealedBytes): Credential? {
        val plain = cipher.decrypt(sealed).decodeToString()
        return Json.decodeFromString(Credential.serializer(), plain)
    }

    private companion object {
        val IV_KEY = stringPreferencesKey("vault_iv")
        val CIPHERTEXT_KEY = stringPreferencesKey("vault_ciphertext")

        fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
        fun decode(text: String): ByteArray = Base64.getDecoder().decode(text)
    }
}
