package io.coreback.relayremote.data

import javax.crypto.KeyGenerator
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CredentialCipherTest {
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    @Test
    fun `credential envelope round trips and does not contain plaintext`() {
        val cipher = CredentialCipher(SecretKeyProvider { key })
        val plaintext = "identifier:private-key:public-key".toByteArray()

        val envelope = cipher.encrypt("living-room", plaintext)

        assertFalse(envelope.toList().windowed(plaintext.size).any {
            it.toByteArray().contentEquals(plaintext)
        })
        assertArrayEquals(plaintext, cipher.decrypt("living-room", envelope))
    }

    @Test
    fun `credential envelope is bound to device identifier`() {
        val cipher = CredentialCipher(SecretKeyProvider { key })
        val envelope = cipher.encrypt("living-room", "credentials".toByteArray())

        org.junit.Assert.assertThrows(SecurityException::class.java) {
            cipher.decrypt("bedroom", envelope)
        }
    }
}
