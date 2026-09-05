package io.coreback.relayremote.protocol.pairing

import io.coreback.relayremote.protocol.PairingCredentials
import org.junit.Assert.assertEquals
import org.junit.Test

class PairingCredentialsCodecTest {
    @Test
    fun `credential binary format round trips without text conversion`() {
        val source = PairingCredentials(
            clientIdentifier = "client".toByteArray(),
            clientPrivateKey = ByteArray(32) { it.toByte() },
            clientPublicKey = ByteArray(32) { (it + 1).toByte() },
            accessoryIdentifier = "accessory".toByteArray(),
            accessoryPublicKey = ByteArray(32) { (it + 2).toByte() },
        )

        assertEquals(source, PairingCredentialsCodec.decode(PairingCredentialsCodec.encode(source)))
    }
}
