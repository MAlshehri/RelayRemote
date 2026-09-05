package io.coreback.relayremote.protocol.codec

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

data class OpackResult(
    val value: Any?,
    val consumedBytes: Int,
)

/**
 * Bounded OPACK encoder/decoder for the value types used by Companion Link.
 *
 * The marker mapping follows pyatv's MIT-licensed implementation at the
 * reference commit documented in docs/PROTOCOL_REFERENCE.md.
 */
object OpackCodec {
    private const val MAX_DEPTH = 32
    private const val MAX_VALUE_SIZE = 8 * 1024 * 1024
    private const val MAX_COLLECTION_SIZE = 4096

    fun encode(value: Any?): ByteArray = ByteArrayOutputStream().also {
        encodeValue(value, it, 0)
    }.toByteArray()

    fun decode(bytes: ByteArray): OpackResult {
        if (bytes.isEmpty()) throw ProtocolCodecException("Empty OPACK input")
        if (bytes.size > MAX_VALUE_SIZE) throw ProtocolCodecException("OPACK input is too large")
        val reader = Reader(bytes)
        val value = reader.readValue(0)
        if (reader.remaining != 0) {
            throw ProtocolCodecException("${reader.remaining} trailing OPACK bytes")
        }
        return OpackResult(value, reader.offset)
    }

    private fun encodeValue(value: Any?, out: ByteArrayOutputStream, depth: Int) {
        if (depth > MAX_DEPTH) throw ProtocolCodecException("OPACK nesting exceeds $MAX_DEPTH")
        when (value) {
            null -> out.write(0x04)
            true -> out.write(0x01)
            false -> out.write(0x02)
            is Byte -> encodeInteger(value.toLong(), out)
            is Short -> encodeInteger(value.toLong(), out)
            is Int -> encodeInteger(value.toLong(), out)
            is Long -> encodeInteger(value, out)
            is Float -> {
                out.write(0x35)
                out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(value).array())
            }
            is Double -> {
                out.write(0x36)
                out.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putDouble(value).array())
            }
            is String -> encodeSized(value.toByteArray(Charsets.UTF_8), out, 0x40, 0x61)
            is ByteArray -> encodeSized(value, out, 0x70, 0x91)
            is UUID -> {
                out.write(0x05)
                out.write(
                    ByteBuffer.allocate(16)
                        .order(ByteOrder.BIG_ENDIAN)
                        .putLong(value.mostSignificantBits)
                        .putLong(value.leastSignificantBits)
                        .array(),
                )
            }
            is List<*> -> {
                if (value.size > MAX_COLLECTION_SIZE) {
                    throw ProtocolCodecException("OPACK list is too large")
                }
                if (value.size < 15) {
                    out.write(0xD0 + value.size)
                    value.forEach { encodeValue(it, out, depth + 1) }
                } else {
                    out.write(0xDF)
                    value.forEach { encodeValue(it, out, depth + 1) }
                    out.write(0x03)
                }
            }
            is Map<*, *> -> {
                if (value.size > MAX_COLLECTION_SIZE) {
                    throw ProtocolCodecException("OPACK map is too large")
                }
                if (value.size < 15) {
                    out.write(0xE0 + value.size)
                    value.forEach { (key, item) ->
                        encodeValue(key, out, depth + 1)
                        encodeValue(item, out, depth + 1)
                    }
                } else {
                    out.write(0xEF)
                    value.forEach { (key, item) ->
                        encodeValue(key, out, depth + 1)
                        encodeValue(item, out, depth + 1)
                    }
                    out.write(0x03)
                }
            }
            else -> throw ProtocolCodecException("Unsupported OPACK type: ${value::class.java.name}")
        }
    }

    private fun encodeInteger(value: Long, out: ByteArrayOutputStream) {
        if (value < 0) throw ProtocolCodecException("Negative OPACK integers are unsupported")
        when {
            value < 0x28 -> out.write((value + 8).toInt())
            value <= 0xFF -> {
                out.write(0x30)
                out.write(value.toInt())
            }
            value <= 0xFFFF -> {
                out.write(0x31)
                out.write(littleEndian(value, 2))
            }
            value <= 0xFFFF_FFFFL -> {
                out.write(0x32)
                out.write(littleEndian(value, 4))
            }
            else -> {
                out.write(0x33)
                out.write(littleEndian(value, 8))
            }
        }
    }

    private fun encodeSized(
        bytes: ByteArray,
        out: ByteArrayOutputStream,
        inlineBase: Int,
        sizedBase: Int,
    ) {
        if (bytes.size > MAX_VALUE_SIZE) throw ProtocolCodecException("OPACK value is too large")
        if (bytes.size <= 0x20) {
            out.write(inlineBase + bytes.size)
            out.write(bytes)
            return
        }
        val lengthBytes = when {
            bytes.size <= 0xFF -> 1
            bytes.size <= 0xFFFF -> 2
            else -> 4
        }
        val marker = if (sizedBase == 0x91) {
            when (lengthBytes) {
                1 -> 0x91
                2 -> 0x92
                else -> 0x93
            }
        } else {
            0x60 + lengthBytes
        }
        out.write(marker)
        out.write(littleEndian(bytes.size.toLong(), lengthBytes))
        out.write(bytes)
    }

    private fun littleEndian(value: Long, size: Int): ByteArray =
        ByteArray(size) { index -> (value ushr (index * 8)).toByte() }

    private class Reader(private val bytes: ByteArray) {
        var offset: Int = 0
            private set
        val remaining: Int get() = bytes.size - offset
        private val objects = mutableListOf<Any?>()

        fun readValue(depth: Int): Any? {
            if (depth > MAX_DEPTH) throw ProtocolCodecException("OPACK nesting exceeds $MAX_DEPTH")
            val marker = readUnsignedByte()
            var track = true
            val value: Any? = when {
                marker == 0x01 -> true.also { track = false }
                marker == 0x02 -> false.also { track = false }
                marker == 0x04 -> null.also { track = false }
                marker == 0x05 -> UUID(
                    readLong(ByteOrder.BIG_ENDIAN),
                    readLong(ByteOrder.BIG_ENDIAN),
                )
                marker == 0x06 -> readLong(ByteOrder.LITTLE_ENDIAN)
                marker in 0x08..0x2F -> (marker - 8).toLong().also { track = false }
                marker == 0x35 -> readBuffer(4).order(ByteOrder.LITTLE_ENDIAN).float
                marker == 0x36 -> readBuffer(8).order(ByteOrder.LITTLE_ENDIAN).double
                marker in 0x30..0x33 -> readUnsignedInteger(1 shl (marker and 0x0F))
                marker in 0x40..0x60 ->
                    readBytes(marker - 0x40).toString(Charsets.UTF_8)
                marker in 0x61..0x64 -> {
                    val size = readLength(marker and 0x0F)
                    readBytes(size).toString(Charsets.UTF_8)
                }
                marker in 0x70..0x90 -> readBytes(marker - 0x70)
                marker in 0x91..0x94 -> {
                    val lengthBytes = 1 shl ((marker and 0x0F) - 1)
                    readBytes(readLength(lengthBytes))
                }
                marker in 0xD0..0xDF -> readList(marker and 0x0F, depth).also { track = false }
                marker in 0xE0..0xEF -> readMap(marker and 0x0F, depth).also { track = false }
                marker in 0xA0..0xC0 -> {
                    track = false
                    objectAt(marker - 0xA0)
                }
                marker in 0xC1..0xC4 -> {
                    track = false
                    objectAt(readLength(marker - 0xC0))
                }
                else -> throw ProtocolCodecException("Unsupported OPACK marker 0x${marker.toString(16)}")
            }
            if (track && objects.none { opackEquals(it, value) }) objects += value
            return value
        }

        private fun readList(count: Int, depth: Int): List<Any?> {
            val output = mutableListOf<Any?>()
            if (count == 15) {
                while (peekUnsignedByte() != 0x03) {
                    if (output.size >= MAX_COLLECTION_SIZE) {
                        throw ProtocolCodecException("OPACK list is too large")
                    }
                    output += readValue(depth + 1)
                }
                offset++
            } else {
                repeat(count) { output += readValue(depth + 1) }
            }
            return output
        }

        private fun readMap(count: Int, depth: Int): Map<Any?, Any?> {
            val output = linkedMapOf<Any?, Any?>()
            if (count == 15) {
                while (peekUnsignedByte() != 0x03) {
                    if (output.size >= MAX_COLLECTION_SIZE) {
                        throw ProtocolCodecException("OPACK map is too large")
                    }
                    output[readValue(depth + 1)] = readValue(depth + 1)
                }
                offset++
            } else {
                repeat(count) {
                    output[readValue(depth + 1)] = readValue(depth + 1)
                }
            }
            return output
        }

        private fun objectAt(index: Int): Any? =
            objects.getOrNull(index)
                ?: throw ProtocolCodecException("Invalid OPACK object reference $index")

        private fun readLength(byteCount: Int): Int {
            val value = readUnsignedInteger(byteCount)
            if (value > MAX_VALUE_SIZE) throw ProtocolCodecException("OPACK value is too large")
            return value.toInt()
        }

        private fun readUnsignedInteger(byteCount: Int): Long {
            if (byteCount !in setOf(1, 2, 4, 8)) {
                throw ProtocolCodecException("Invalid OPACK integer size $byteCount")
            }
            val data = readBytes(byteCount)
            if (byteCount == 8 && data[7].toInt() < 0) {
                throw ProtocolCodecException("Unsigned OPACK integer exceeds Long range")
            }
            var result = 0L
            data.forEachIndexed { index, byte ->
                result = result or ((byte.toLong() and 0xFF) shl (index * 8))
            }
            return result
        }

        private fun readLong(order: ByteOrder): Long = readBuffer(8).order(order).long

        private fun readBuffer(size: Int): ByteBuffer = ByteBuffer.wrap(readBytes(size))

        private fun readBytes(size: Int): ByteArray {
            if (size < 0 || remaining < size) {
                throw ProtocolCodecException("Truncated OPACK value at $offset")
            }
            return bytes.copyOfRange(offset, offset + size).also { offset += size }
        }

        private fun readUnsignedByte(): Int {
            if (remaining == 0) throw ProtocolCodecException("Truncated OPACK input")
            return bytes[offset++].toInt() and 0xFF
        }

        private fun peekUnsignedByte(): Int {
            if (remaining == 0) throw ProtocolCodecException("Missing OPACK terminator")
            return bytes[offset].toInt() and 0xFF
        }

        private fun opackEquals(left: Any?, right: Any?): Boolean =
            if (left is ByteArray && right is ByteArray) left.contentEquals(right) else left == right
    }
}
