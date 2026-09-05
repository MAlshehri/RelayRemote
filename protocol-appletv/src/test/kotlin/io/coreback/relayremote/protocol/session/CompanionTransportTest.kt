package io.coreback.relayremote.protocol.session

import io.coreback.relayremote.protocol.codec.CompanionFrameCodec
import io.coreback.relayremote.protocol.codec.CompanionFrameType
import java.net.ServerSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class CompanionTransportTest {
    @Test
    fun `tcp transport writes and incrementally receives companion frames`() = runBlocking {
        val server = ServerSocket(0)
        val serverWork = async(Dispatchers.IO) {
            server.accept().use { socket ->
                val header = socket.getInputStream().readNBytes(4)
                val size = ((header[1].toInt() and 0xff) shl 16) or
                    ((header[2].toInt() and 0xff) shl 8) or
                    (header[3].toInt() and 0xff)
                assertEquals(CompanionFrameType.E_OPACK.code, header[0].toInt())
                assertArrayEquals(byteArrayOf(1, 2, 3), socket.getInputStream().readNBytes(size))

                val reply = CompanionFrameCodec.encode(
                    CompanionFrameType.PS_NEXT,
                    byteArrayOf(9, 8),
                )
                socket.getOutputStream().write(reply, 0, 3)
                socket.getOutputStream().flush()
                socket.getOutputStream().write(reply, 3, reply.size - 3)
                socket.getOutputStream().flush()
            }
        }

        val transport = CompanionTransport()
        transport.connect("127.0.0.1", server.localPort)
        transport.send(CompanionFrameType.E_OPACK, byteArrayOf(1, 2, 3))
        val frame = withTimeout(2_000) { transport.receive() }

        assertEquals(CompanionFrameType.PS_NEXT, frame.type)
        assertArrayEquals(byteArrayOf(9, 8), frame.payload)
        transport.close()
        serverWork.await()
        withContext(Dispatchers.IO) { server.close() }
    }
}
