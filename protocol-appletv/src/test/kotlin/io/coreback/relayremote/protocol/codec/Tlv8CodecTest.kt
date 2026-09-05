package io.coreback.relayremote.protocol.codec

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class Tlv8CodecTest {
    @Test
    fun `values larger than 255 bytes round trip across chunks`() {
        val longValue = ByteArray(300) { it.toByte() }
        val encoded = Tlv8Codec.encode(linkedMapOf(1 to longValue, 6 to byteArrayOf(3)))
        val decoded = Tlv8Codec.decode(encoded)

        assertArrayEquals(longValue, decoded.getValue(1))
        assertArrayEquals(byteArrayOf(3), decoded.getValue(6))
        assertEquals(1, encoded[0].toInt())
        assertEquals(255, encoded[1].toInt() and 0xFF)
        assertEquals(1, encoded[257].toInt())
        assertEquals(45, encoded[258].toInt())
    }

    @Test(expected = ProtocolCodecException::class)
    fun `truncated entry is rejected`() {
        Tlv8Codec.decode(byteArrayOf(1, 3, 9))
    }
}
