package io.coreback.relayremote.protocol.codec

enum class CompanionFrameType(val code: Int) {
    NO_OP(1),
    PS_START(3),
    PS_NEXT(4),
    PV_START(5),
    PV_NEXT(6),
    U_OPACK(7),
    E_OPACK(8),
    P_OPACK(9),
    PAIRING_REQUEST(10),
    PAIRING_RESPONSE(11),
    SESSION_START_REQUEST(16),
    SESSION_START_RESPONSE(17),
    SESSION_DATA(18),
    FAMILY_IDENTITY_REQUEST(32),
    FAMILY_IDENTITY_RESPONSE(33),
    FAMILY_IDENTITY_UPDATE(34);

    companion object {
        fun fromCode(code: Int): CompanionFrameType =
            entries.firstOrNull { it.code == code }
                ?: throw ProtocolCodecException("Unknown Companion frame type: $code")
    }
}

data class CompanionFrame(
    val type: CompanionFrameType,
    val payload: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        other is CompanionFrame && type == other.type && payload.contentEquals(other.payload)

    override fun hashCode(): Int = 31 * type.hashCode() + payload.contentHashCode()
}

object CompanionFrameCodec {
    private const val MAX_WIRE_PAYLOAD = 0xFF_FFFF

    fun encode(type: CompanionFrameType, payload: ByteArray): ByteArray {
        if (payload.size > MAX_WIRE_PAYLOAD) {
            throw ProtocolCodecException("Companion payload exceeds 24-bit length")
        }
        return byteArrayOf(
            type.code.toByte(),
            (payload.size ushr 16).toByte(),
            (payload.size ushr 8).toByte(),
            payload.size.toByte(),
        ) + payload
    }
}

class CompanionFrameDecoder(
    private val maxPayloadSize: Int = 8 * 1024 * 1024,
) {
    private var buffer = ByteArray(0)

    init {
        require(maxPayloadSize in 0..0xFF_FFFF)
    }

    fun feed(bytes: ByteArray): List<CompanionFrame> {
        if (bytes.isNotEmpty()) buffer += bytes
        val frames = mutableListOf<CompanionFrame>()
        var offset = 0
        while (buffer.size - offset >= HEADER_SIZE) {
            val payloadSize =
                ((buffer[offset + 1].toInt() and 0xFF) shl 16) or
                    ((buffer[offset + 2].toInt() and 0xFF) shl 8) or
                    (buffer[offset + 3].toInt() and 0xFF)
            if (payloadSize > maxPayloadSize) {
                buffer = ByteArray(0)
                throw ProtocolCodecException(
                    "Companion payload $payloadSize exceeds limit $maxPayloadSize",
                )
            }
            val frameSize = HEADER_SIZE + payloadSize
            if (buffer.size - offset < frameSize) break
            val type = CompanionFrameType.fromCode(buffer[offset].toInt() and 0xFF)
            frames += CompanionFrame(
                type = type,
                payload = buffer.copyOfRange(offset + HEADER_SIZE, offset + frameSize),
            )
            offset += frameSize
        }
        if (offset > 0) buffer = buffer.copyOfRange(offset, buffer.size)
        return frames
    }

    private companion object {
        const val HEADER_SIZE = 4
    }
}
