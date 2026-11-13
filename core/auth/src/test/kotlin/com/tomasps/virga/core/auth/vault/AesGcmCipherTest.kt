package com.tomasps.virga.core.auth.vault

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class AesGcmCipherTest {

    @Test
    fun `decrypting what was encrypted returns the original bytes`() {
        // Arrange
        val key = newKey()
        val cipher = AesGcmCipher { key }
        val plain = "cf-token-example".encodeToByteArray()

        // Act
        val sealed = cipher.encrypt(plain)

        // Assert
        assertArrayEquals(plain, cipher.decrypt(sealed))
    }

    @Test
    fun `the same plaintext gets a fresh IV and different ciphertext each time`() {
        // Arrange
        val key = newKey()
        val cipher = AesGcmCipher { key }
        val plain = "same".encodeToByteArray()

        // Act
        val first = cipher.encrypt(plain)
        val second = cipher.encrypt(plain)

        // Assert
        assertNotEquals(first.iv.toList(), second.iv.toList())
        assertFalse(first.ciphertext.toList() == second.ciphertext.toList())
    }

    @Test
    fun `ciphertext that was tampered with is rejected rather than decrypted`() {
        // Arrange
        val key = newKey()
        val cipher = AesGcmCipher { key }
        val sealed = cipher.encrypt("tamper-me".encodeToByteArray())
        val flipped = sealed.ciphertext.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }

        // Act and Assert
        assertThrows(Exception::class.java) {
            cipher.decrypt(SealedBytes(sealed.iv, flipped))
        }
    }

    @Test
    fun `a different key cannot decrypt the bytes`() {
        // Arrange
        val writer = AesGcmCipher { newKey() }
        val reader = AesGcmCipher { newKey() }
        val sealed = writer.encrypt("secret".encodeToByteArray())

        // Act and Assert
        assertThrows(Exception::class.java) { reader.decrypt(sealed) }
    }

    private fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
}
