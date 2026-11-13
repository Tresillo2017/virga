package com.tomasps.virga.core.auth.vault

import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM. The IV is chosen by the cipher, never by the caller, because a Keystore key that requires
 * randomized encryption refuses a caller-supplied IV. [keyProvider] is called per operation so the key can
 * be read from the Keystore lazily.
 */
class AesGcmCipher(private val keyProvider: () -> SecretKey) : SecretCipher {

    override fun encrypt(plaintext: ByteArray): SealedBytes {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyProvider())
        return SealedBytes(iv = cipher.iv, ciphertext = cipher.doFinal(plaintext))
    }

    /** Throws [GeneralSecurityException] when the bytes were tampered with or the key differs. */
    override fun decrypt(sealed: SealedBytes): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keyProvider(), GCMParameterSpec(TAG_BITS, sealed.iv))
        return cipher.doFinal(sealed.ciphertext)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
    }
}
