package io.coreback.relayremote.protocol.session

import io.coreback.relayremote.protocol.codec.CompanionFrame
import io.coreback.relayremote.protocol.codec.CompanionFrameCodec
import io.coreback.relayremote.protocol.codec.CompanionFrameType
import io.coreback.relayremote.protocol.codec.ProtocolCodecException
import io.coreback.relayremote.protocol.crypto.CompanionSessionCipher
import java.io.EOFException
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface CompanionFrameTransport {
    val connected: Boolean
    suspend fun connect(host: String, port: Int)
    fun enableEncryption(outputKey: ByteArray, inputKey: ByteArray)
    suspend fun send(type: CompanionFrameType, plaintext: ByteArray)
    suspend fun receive(): CompanionFrame
    fun close()
}

class CompanionTransport(
    private val connectTimeoutMillis: Int = 5_000,
    private val maxPayloadSize: Int = 8 * 1024 * 1024,
) : CompanionFrameTransport {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()
    private val incoming = Channel<CompanionFrame>(capacity = 64)
    private var socket: Socket? = null
    private var readerJob: Job? = null
    @Volatile private var cipher: CompanionSessionCipher? = null

    override val connected: Boolean
        get() = socket?.isConnected == true && socket?.isClosed == false

    override suspend fun connect(host: String, port: Int) {
        require(port in 1..65535)
        check(socket == null) { "Transport is already connected" }
        val connectedSocket = withContext(Dispatchers.IO) {
            Socket().apply {
                tcpNoDelay = true
                keepAlive = true
                connect(InetSocketAddress(host, port), connectTimeoutMillis)
            }
        }
        socket = connectedSocket
        readerJob = scope.launch { readFrames(connectedSocket) }
    }

    override fun enableEncryption(outputKey: ByteArray, inputKey: ByteArray) {
        check(connected)
        check(cipher == null) { "Transport encryption is already enabled" }
        cipher = CompanionSessionCipher(outputKey.copyOf(), inputKey.copyOf())
    }

    override suspend fun send(type: CompanionFrameType, plaintext: ByteArray) {
        writeMutex.withLock {
            val activeSocket = socket ?: throw EOFException("Companion transport is closed")
            val activeCipher = cipher
            val wirePayload: ByteArray
            val header: ByteArray
            if (activeCipher == null || plaintext.isEmpty()) {
                wirePayload = plaintext
                header = CompanionFrameCodec.encode(type, plaintext).copyOfRange(0, 4)
            } else {
                val encryptedSize = plaintext.size + AUTH_TAG_SIZE
                header = frameHeader(type, encryptedSize)
                wirePayload = activeCipher.encrypt(header, plaintext)
            }
            withContext(Dispatchers.IO) {
                activeSocket.getOutputStream().apply {
                    write(header)
                    write(wirePayload)
                    flush()
                }
            }
        }
    }

    override suspend fun receive(): CompanionFrame = incoming.receive()

    override fun close() {
        readerJob?.cancel()
        readerJob = null
        try {
            socket?.close()
        } catch (_: Exception) {
            // Socket is already closing.
        }
        socket = null
        cipher?.destroy()
        cipher = null
    }

    private suspend fun readFrames(activeSocket: Socket) {
        try {
            val input = activeSocket.getInputStream()
            while (scope.isActive && !activeSocket.isClosed) {
                val header = withContext(Dispatchers.IO) { input.readNBytes(4) }
                if (header.isEmpty()) throw EOFException("Apple TV closed the connection")
                if (header.size != 4) throw EOFException("Truncated Companion frame header")
                val payloadSize =
                    ((header[1].toInt() and 0xFF) shl 16) or
                        ((header[2].toInt() and 0xFF) shl 8) or
                        (header[3].toInt() and 0xFF)
                if (payloadSize > maxPayloadSize + AUTH_TAG_SIZE) {
                    throw ProtocolCodecException("Companion frame exceeds configured limit")
                }
                val wirePayload = withContext(Dispatchers.IO) { input.readNBytes(payloadSize) }
                if (wirePayload.size != payloadSize) {
                    throw EOFException("Truncated Companion frame payload")
                }
                val activeCipher = cipher
                val plaintext = if (activeCipher != null && wirePayload.isNotEmpty()) {
                    activeCipher.decrypt(header, wirePayload)
                } else {
                    wirePayload
                }
                incoming.send(
                    CompanionFrame(
                        CompanionFrameType.fromCode(header[0].toInt() and 0xFF),
                        plaintext,
                    ),
                )
            }
        } catch (error: Throwable) {
            incoming.close(error)
        } finally {
            try {
                activeSocket.close()
            } catch (_: Exception) {
                // Socket is already closed.
            }
        }
    }

    private fun frameHeader(type: CompanionFrameType, size: Int): ByteArray {
        if (size !in 0..0xFF_FFFF) throw ProtocolCodecException("Invalid frame size")
        return byteArrayOf(
            type.code.toByte(),
            (size ushr 16).toByte(),
            (size ushr 8).toByte(),
            size.toByte(),
        )
    }

    private companion object {
        const val AUTH_TAG_SIZE = 16
    }
}
