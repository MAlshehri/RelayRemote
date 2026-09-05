package io.coreback.relayremote.protocol.session

import io.coreback.relayremote.protocol.codec.CompanionFrameType
import io.coreback.relayremote.protocol.codec.OpackCodec
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

class CompanionProtocolException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

class CompanionProtocol(
    private val transport: CompanionFrameTransport,
    private val scope: CoroutineScope,
    private val eventListener: (String, Map<String, Any?>) -> Unit = { _, _ -> },
    private val timeoutMillis: Long = 5_000,
) {
    private val nextXid = AtomicLong(1)
    private val pending = ConcurrentHashMap<Long, CompletableDeferred<Map<String, Any?>>>()
    private val pendingAuth =
        ConcurrentHashMap<CompanionFrameType, CompletableDeferred<Map<String, Any?>>>()
    private val authMutex = Mutex()
    private var readerJob: Job? = null

    fun start() {
        check(readerJob == null) { "Companion protocol already started" }
        readerJob = scope.launch { readLoop() }
    }

    suspend fun exchange(
        identifier: String,
        content: Map<String, Any?>,
    ): Map<String, Any?> {
        val xid = nextXid.getAndIncrement()
        if (xid < 0) throw CompanionProtocolException("Companion transaction ID exhausted")
        val response = CompletableDeferred<Map<String, Any?>>()
        pending[xid] = response
        try {
            transport.send(
                CompanionFrameType.E_OPACK,
                OpackCodec.encode(
                    linkedMapOf(
                        "_i" to identifier,
                        "_t" to MESSAGE_REQUEST,
                        "_c" to content,
                        "_x" to xid,
                    ),
                ),
            )
            return withTimeout(timeoutMillis) { response.await() }
        } finally {
            pending.remove(xid)
        }
    }

    suspend fun sendEvent(identifier: String, content: Map<String, Any?>) {
        val xid = nextXid.getAndIncrement()
        transport.send(
            CompanionFrameType.E_OPACK,
            OpackCodec.encode(
                linkedMapOf(
                    "_i" to identifier,
                    "_t" to MESSAGE_EVENT,
                    "_c" to content,
                    "_x" to xid,
                ),
            ),
        )
    }

    suspend fun exchangeAuth(
        requestType: CompanionFrameType,
        content: Map<String, Any?>,
    ): Map<String, Any?> = authMutex.withLock {
        val responseType = when (requestType) {
            CompanionFrameType.PS_START -> CompanionFrameType.PS_NEXT
            CompanionFrameType.PV_START -> CompanionFrameType.PV_NEXT
            CompanionFrameType.PS_NEXT,
            CompanionFrameType.PV_NEXT,
            -> requestType
            else -> throw CompanionProtocolException("Not an authentication frame")
        }
        val response = CompletableDeferred<Map<String, Any?>>()
        pendingAuth[responseType] = response
        try {
            transport.send(requestType, OpackCodec.encode(content))
            withTimeout(timeoutMillis) { response.await() }
        } finally {
            pendingAuth.remove(responseType)
        }
    }

    fun close() {
        readerJob?.cancel()
        readerJob = null
        val error = CompanionProtocolException("Companion protocol closed")
        pending.values.forEach { it.completeExceptionally(error) }
        pendingAuth.values.forEach { it.completeExceptionally(error) }
        pending.clear()
        pendingAuth.clear()
        transport.close()
    }

    private suspend fun readLoop() {
        try {
            while (true) {
                val frame = transport.receive()
                val decoded = OpackCodec.decode(frame.payload).value.asStringMap()
                if (frame.type in AUTH_TYPES) {
                    pendingAuth.remove(frame.type)?.complete(decoded)
                    continue
                }
                when ((decoded["_t"] as? Number)?.toLong()) {
                    MESSAGE_RESPONSE -> {
                        val xid = (decoded["_x"] as? Number)?.toLong() ?: continue
                        val destination = pending.remove(xid) ?: continue
                        val message = decoded["_em"] as? String
                        if (message != null) {
                            destination.completeExceptionally(
                                CompanionProtocolException("Apple TV rejected the command: $message"),
                            )
                        } else {
                            destination.complete(decoded["_c"].asStringMap())
                        }
                    }
                    MESSAGE_EVENT -> {
                        val name = decoded["_i"] as? String ?: continue
                        eventListener(name, decoded["_c"].asStringMap())
                    }
                }
            }
        } catch (error: Throwable) {
            pending.values.forEach { it.completeExceptionally(error) }
            pendingAuth.values.forEach { it.completeExceptionally(error) }
            pending.clear()
            pendingAuth.clear()
        }
    }

    private fun Any?.asStringMap(): Map<String, Any?> {
        val map = this as? Map<*, *> ?: return emptyMap()
        return map.entries.mapNotNull { (key, value) ->
            (key as? String)?.let { it to value }
        }.toMap(LinkedHashMap())
    }

    private companion object {
        const val MESSAGE_EVENT = 1L
        const val MESSAGE_REQUEST = 2L
        const val MESSAGE_RESPONSE = 3L
        val AUTH_TYPES = setOf(
            CompanionFrameType.PS_START,
            CompanionFrameType.PS_NEXT,
            CompanionFrameType.PV_START,
            CompanionFrameType.PV_NEXT,
        )
    }
}
