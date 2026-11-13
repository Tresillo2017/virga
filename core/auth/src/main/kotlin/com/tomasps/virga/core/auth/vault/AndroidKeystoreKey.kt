package com.tomasps.virga.core.auth.vault

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * The vault key. Generated inside the Android Keystore, so it cannot be exported: getEncoded returns null
 * and the key bytes never reach app memory. Creating it on first use keeps first launch cheap.
 */
object AndroidKeystoreKey {
    const val ALIAS = "virga_credential_vault_v1"
    private const val PROVIDER = "AndroidKeyStore"
    private const val KEY_SIZE_BITS = 256

    /**
     * Synchronized so two first-run callers cannot each generate a key under the same alias; the second would
     * overwrite the first and make the ciphertext written under it unreadable.
     */
    @Synchronized
    fun getOrCreate(): SecretKey {
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
            .setRandomizedEncryptionRequired(true)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
            .apply { init(spec) }
            .generateKey()
    }
}
