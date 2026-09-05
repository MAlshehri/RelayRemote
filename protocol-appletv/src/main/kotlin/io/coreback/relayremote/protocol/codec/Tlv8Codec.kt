package io.coreback.relayremote.protocol.codec

import java.io.ByteArrayOutputStream

object Tlv8Codec {
    fun encode(values: Map<Int, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        values.forEach { (tag, value) ->
            require(tag in 0..255) { "TLV8 tag must fit in one byte" }
            if (value.isEmpty()) {
                output.write(tag)
                output.write(0)
            } else {
                var offset = 0
                while (offset < value.size) {
                    val size = minOf(255, value.size - offset)
                    output.write(tag)
                    output.write(size)
                    output.write(value, offset, size)
                    offset += size
                }
            }
        }
        return output.toByteArray()
    }

    fun decode(bytes: ByteArray): Map<Int, ByteArray> {
        val output = linkedMapOf<Int, ByteArray>()
        var offset = 0
        while (offset < bytes.size) {
            if (bytes.size - offset < 2) {
                throw ProtocolCodecException("Truncated TLV8 header at $offset")
            }
            val tag = bytes[offset].toInt() and 0xFF
            val length = bytes[offset + 1].toInt() and 0xFF
            offset += 2
            if (bytes.size - offset < length) {
                throw ProtocolCodecException("Truncated TLV8 value for tag $tag")
            }
            val chunk = bytes.copyOfRange(offset, offset + length)
            output[tag] = output[tag]?.plus(chunk) ?: chunk
            offset += length
        }
        return output
    }
}
