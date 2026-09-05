package io.coreback.relayremote.protocol.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class HapCryptoTest {
    @Test
    fun `hkdf sha512 matches independent openssl vector`() {
        val output = HapCrypto.hkdfSha512(
            inputKeyMaterial = ByteArray(22) { 0x0b },
            salt = ByteArray(22),
            info = hex("f0f1f2f3f4f5f6f7f8f9"),
            outputLength = 42,
        )

        assertArrayEquals(
            hex(
                "5381877f912df8257626c548528eff28829b76d7d528e9590d68c8b17a0086d7" +
                    "0a5e1328d508c25479d8",
            ),
            output,
        )
    }

    @Test
    fun `chacha20 poly1305 authenticates aad and ciphertext`() {
        val key = ByteArray(32) { it.toByte() }
        val nonce = ByteArray(12).also { it[11] = 1 }
        val aad = "frame-header".toByteArray()
        val plaintext = "secret companion payload".toByteArray()
        val encrypted = HapCrypto.chachaEncrypt(key, nonce, aad, plaintext)

        assertArrayEquals(
            plaintext,
            HapCrypto.chachaDecrypt(key, nonce, aad, encrypted),
        )
        encrypted[0] = (encrypted[0].toInt() xor 1).toByte()
        org.junit.Assert.assertThrows(SecurityException::class.java) {
            HapCrypto.chachaDecrypt(key, nonce, aad, encrypted)
        }
    }

    private fun hex(value: String): ByteArray =
        value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
