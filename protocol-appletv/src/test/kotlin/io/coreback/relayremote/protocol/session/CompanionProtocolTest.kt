package io.coreback.relayremote.protocol.session

import io.coreback.relayremote.protocol.codec.CompanionFrame
import io.coreback.relayremote.protocol.codec.CompanionFrameType
import io.coreback.relayremote.protocol.codec.OpackCodec
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CompanionProtocolTest {
    @Test
    fun `opack responses correlate by xid and events dispatch independently`() = runTest {
        val transport = FakeCompanionFrameTransport()
        val events = mutableListOf<Pair<String, Map<String, Any?>>>()
        val protocol = CompanionProtocol(
            transport = transport,
            scope = backgroundScope,
            eventListener = { name, content -> events += name to content },
        )
        protocol.start()

        val response = async {
            protocol.exchange("_hidC", mapOf("_hidC" to 1L))
        }
        runCurrent()
        val request = OpackCodec.decode(transport.sent.single().payload).value as Map<*, *>
        val xid = request["_x"] as Long
        assertEquals(2L, request["_t"])
        assertEquals("_hidC", request["_i"])

        transport.emit(
            CompanionFrameType.E_OPACK,
            mapOf("_t" to 1L, "_i" to "_iMC", "_c" to mapOf("_mcF" to 0x100L)),
        )
        transport.emit(
            CompanionFrameType.E_OPACK,
            mapOf("_t" to 3L, "_x" to xid, "_c" to mapOf("ok" to true)),
        )
        runCurrent()

        assertEquals(true, response.await()["ok"])
        assertEquals("_iMC", events.single().first)
        assertEquals(0x100L, events.single().second["_mcF"])
    }
}

private class FakeCompanionFrameTransport : CompanionFrameTransport {
    private val incoming = Channel<CompanionFrame>(Channel.UNLIMITED)
    val sent = mutableListOf<CompanionFrame>()
    override val connected: Boolean = true

    override suspend fun connect(host: String, port: Int) = Unit

    override fun enableEncryption(outputKey: ByteArray, inputKey: ByteArray) = Unit

    override suspend fun send(type: CompanionFrameType, plaintext: ByteArray) {
        sent += CompanionFrame(type, plaintext)
    }

    override suspend fun receive(): CompanionFrame = incoming.receive()

    override fun close() {
        incoming.close()
    }

    suspend fun emit(type: CompanionFrameType, value: Map<String, Any?>) {
        incoming.send(CompanionFrame(type, OpackCodec.encode(value)))
    }
}
