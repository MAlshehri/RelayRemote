package io.coreback.relayremote.protocol.pairing

import io.coreback.relayremote.protocol.PairingCredentials
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

object PairingCredentialsCodec {
    private val MAGIC = byteArrayOf(0x52, 0x52, 0x43, 0x44) // RRCD

    fun encode(credentials: PairingCredentials): ByteArray =
        ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { output ->
                output.write(MAGIC)
                output.writeByte(1)
                output.writeSized(credentials.clientIdentifier)
                output.writeSized(credentials.clientPrivateKey)
                output.writeSized(credentials.clientPublicKey)
                output.writeSized(credentials.accessoryIdentifier)
                output.writeSized(credentials.accessoryPublicKey)
            }
        }.toByteArray()

    fun decode(bytes: ByteArray): PairingCredentials {
        if (bytes.size > MAX_CREDENTIAL_BYTES) {
            throw SecurityException("Credential payload is too large")
        }
        return try {
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                if (!input.readNBytes(MAGIC.size).contentEquals(MAGIC) || input.readUnsignedByte() != 1) {
                    throw SecurityException("Unsupported credential payload")
                }
                val credentials = PairingCredentials(
                    clientIdentifier = input.readSized(),
                    clientPrivateKey = input.readSized(),
                    clientPublicKey = input.readSized(),
                    accessoryIdentifier = input.readSized(),
                    accessoryPublicKey = input.readSized(),
                )
                if (input.available() != 0) throw SecurityException("Trailing credential data")
                require(credentials.clientPrivateKey.size == 32)
                require(credentials.clientPublicKey.size == 32)
                require(credentials.accessoryPublicKey.size == 32)
                credentials
            }
        } catch (error: SecurityException) {
            throw error
        } catch (error: Exception) {
            throw SecurityException("Invalid credential payload", error)
        }
    }

    private fun DataOutputStream.writeSized(value: ByteArray) {
        require(value.size <= MAX_FIELD_BYTES)
        writeShort(value.size)
        write(value)
    }

    private fun DataInputStream.readSized(): ByteArray {
        val size = readUnsignedShort()
        if (size > MAX_FIELD_BYTES || available() < size) {
            throw SecurityException("Invalid credential field length")
        }
        return readNBytes(size)
    }

    private const val MAX_FIELD_BYTES = 1024
    private const val MAX_CREDENTIAL_BYTES = 6 * 1024
}
