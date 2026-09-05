package io.coreback.relayremote.protocol.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CompanionSessionCipherTest {
    @Test
    fun `directional peers use independent monotonic little endian nonces`() {
        val clientToServer = ByteArray(32) { it.toByte() }
        val serverToClient = ByteArray(32) { (it + 32).toByte() }
        val client = CompanionSessionCipher(clientToServer, serverToClient)
        val server = CompanionSessionCipher(serverToClient, clientToServer)
        val aad = byteArrayOf(8, 0, 0, 20)

        val first = client.encrypt(aad, "first".toByteArray())
        val second = client.encrypt(aad, "second".toByteArray())

        assertFalse(first.contentEquals(second))
        assertArrayEquals("first".toByteArray(), server.decrypt(aad, first))
        assertArrayEquals("second".toByteArray(), server.decrypt(aad, second))
    }
}
