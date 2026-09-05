package io.coreback.relayremote.protocol.codec

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class CompanionFrameCodecTest {
    @Test
    fun `incremental decoder preserves partial and consecutive frames`() {
        val decoder = CompanionFrameDecoder(maxPayloadSize = 32)
        val first = CompanionFrameCodec.encode(CompanionFrameType.E_OPACK, byteArrayOf(1, 2, 3))
        val second = CompanionFrameCodec.encode(CompanionFrameType.PS_NEXT, byteArrayOf(4))

        assertEquals(emptyList<CompanionFrame>(), decoder.feed(first.copyOfRange(0, 5)))
        val frames = decoder.feed(first.copyOfRange(5, first.size) + second)

        assertEquals(2, frames.size)
        assertEquals(CompanionFrameType.E_OPACK, frames[0].type)
        assertArrayEquals(byteArrayOf(1, 2, 3), frames[0].payload)
        assertEquals(CompanionFrameType.PS_NEXT, frames[1].type)
        assertArrayEquals(byteArrayOf(4), frames[1].payload)
    }

    @Test(expected = ProtocolCodecException::class)
    fun `decoder rejects payload beyond configured bound`() {
        CompanionFrameDecoder(maxPayloadSize = 2).feed(
            byteArrayOf(CompanionFrameType.E_OPACK.code.toByte(), 0, 0, 3),
        )
    }
}
