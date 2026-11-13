package com.tomasps.virga.core.auth.vault

/** Encrypts and decrypts small secrets. The Android implementation uses a Keystore key; tests use a JVM key. */
interface SecretCipher {
    fun encrypt(plaintext: ByteArray): SealedBytes
    fun decrypt(sealed: SealedBytes): ByteArray
}

/** Output of [SecretCipher.encrypt]: the IV that was used and the ciphertext with its authentication tag. */
class SealedBytes(val iv: ByteArray, val ciphertext: ByteArray)
