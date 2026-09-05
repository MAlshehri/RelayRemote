package io.coreback.relayremote.protocol.codec

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpackCodecTest {
    @Test
    fun `pyatv companion scalar and collection vectors decode`() {
        assertEquals(0x27L, OpackCodec.decode(byteArrayOf(0x2f)).value)
        assertEquals("abc", OpackCodec.decode(byteArrayOf(0x43, 0x61, 0x62, 0x63)).value)
        assertArrayEquals(
            byteArrayOf(0x12, 0x34, 0x56),
            OpackCodec.decode(byteArrayOf(0x73, 0x12, 0x34, 0x56)).value as ByteArray,
        )
        val list = OpackCodec.decode(
            byteArrayOf(0xd3.toByte(), 0x09, 0x44, 0x74, 0x65, 0x73, 0x74, 0x02),
        ).value as List<*>
        assertEquals(1L, list[0])
        assertEquals("test", list[1])
        assertFalse(list[2] as Boolean)
    }

    @Test
    fun `companion command map round trips`() {
        val source = linkedMapOf<String, Any?>(
            "_i" to "_hidC",
            "_t" to 2L,
            "_c" to linkedMapOf("_hBtS" to 1L, "_hidC" to 5L),
            "_x" to 42L,
            "_binary" to byteArrayOf(1, 2),
            "_ok" to true,
            "_none" to null,
        )

        val decoded = OpackCodec.decode(OpackCodec.encode(source)).value as Map<*, *>

        assertEquals("_hidC", decoded["_i"])
        assertEquals(42L, decoded["_x"])
        assertArrayEquals(byteArrayOf(1, 2), decoded["_binary"] as ByteArray)
        assertTrue(decoded["_ok"] as Boolean)
        assertNull(decoded["_none"])
    }

    @Test(expected = ProtocolCodecException::class)
    fun `truncated value is rejected`() {
        OpackCodec.decode(byteArrayOf(0x43, 0x61))
    }

    @Test(expected = ProtocolCodecException::class)
    fun `trailing bytes are rejected`() {
        OpackCodec.decode(byteArrayOf(0x01, 0x02))
    }
}
