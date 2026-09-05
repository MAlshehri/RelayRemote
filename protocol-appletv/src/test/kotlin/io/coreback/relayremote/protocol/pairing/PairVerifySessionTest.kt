package io.coreback.relayremote.protocol.pairing

import io.coreback.relayremote.protocol.PairingCredentials
import io.coreback.relayremote.protocol.codec.CompanionFrameType
import io.coreback.relayremote.protocol.codec.Tlv8Codec
import io.coreback.relayremote.protocol.crypto.HapCrypto
import java.security.SecureRandom
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.math.ec.rfc8032.Ed25519
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PairVerifySessionTest {
    @Test
    fun `pair verify authenticates stored identities and derives directional keys`() = runTest {
        val clientSigning = Ed25519PrivateKeyParameters(SecureRandom())
        val accessorySigning = Ed25519PrivateKeyParameters(SecureRandom())
        val credentials = PairingCredentials(
            clientIdentifier = "client-id".toByteArray(),
            clientPrivateKey = clientSigning.encoded,
            clientPublicKey = clientSigning.generatePublicKey().encoded,
            accessoryIdentifier = "accessory-id".toByteArray(),
            accessoryPublicKey = accessorySigning.generatePublicKey().encoded,
        )
        val fake = FakeAppleTvVerifyExchange(credentials, accessorySigning)

        val keys = PairVerifySession(fake).verify(credentials)

        assertTrue(fake.controllerSignatureVerified)
        assertArrayEquals(fake.serverInputKey, keys.outputKey)
        assertArrayEquals(fake.serverOutputKey, keys.inputKey)
    }
}

private class FakeAppleTvVerifyExchange(
    private val credentials: PairingCredentials,
    private val accessorySigning: Ed25519PrivateKeyParameters,
) : CompanionAuthExchange {
    private val serverCurve = X25519PrivateKeyParameters(SecureRandom())
    private val serverPublic = serverCurve.generatePublicKey().encoded
    private lateinit var shared: ByteArray
    lateinit var serverInputKey: ByteArray
    lateinit var serverOutputKey: ByteArray
    var controllerSignatureVerified = false

    override suspend fun exchangeAuth(
        requestType: CompanionFrameType,
        content: Map<String, Any?>,
    ): Map<String, Any?> = when (requestType) {
        CompanionFrameType.PV_START -> start(content)
        CompanionFrameType.PV_NEXT -> finish(content)
        else -> error("Unexpected frame")
    }

    private fun start(content: Map<String, Any?>): Map<String, Any?> {
        val request = Tlv8Codec.decode(content.getValue("_pd") as ByteArray)
        val clientPublic = request.getValue(HapTlv.PUBLIC_KEY)
        shared = ByteArray(32).also {
            serverCurve.generateSecret(X25519PublicKeyParameters(clientPublic, 0), it, 0)
        }
        val verifyKey = HapCrypto.hkdfSha512(
            shared,
            "Pair-Verify-Encrypt-Salt".toByteArray(),
            "Pair-Verify-Encrypt-Info".toByteArray(),
            32,
        )
        val info = serverPublic + credentials.accessoryIdentifier + clientPublic
        val signature = ByteArray(64)
        accessorySigning.sign(
            Ed25519.Algorithm.Ed25519,
            null,
            info,
            0,
            info.size,
            signature,
            0,
        )
        val encrypted = HapCrypto.chachaEncrypt(
            verifyKey,
            ByteArray(4) + "PV-Msg02".toByteArray(),
            byteArrayOf(),
            Tlv8Codec.encode(
                linkedMapOf(
                    HapTlv.IDENTIFIER to credentials.accessoryIdentifier,
                    HapTlv.SIGNATURE to signature,
                ),
            ),
        )
        pendingClientPublic = clientPublic
        return mapOf(
            "_pd" to Tlv8Codec.encode(
                linkedMapOf(
                    HapTlv.SEQUENCE to byteArrayOf(2),
                    HapTlv.PUBLIC_KEY to serverPublic,
                    HapTlv.ENCRYPTED_DATA to encrypted,
                ),
            ),
        )
    }

    private lateinit var pendingClientPublic: ByteArray

    private fun finish(content: Map<String, Any?>): Map<String, Any?> {
        val request = Tlv8Codec.decode(content.getValue("_pd") as ByteArray)
        val verifyKey = HapCrypto.hkdfSha512(
            shared,
            "Pair-Verify-Encrypt-Salt".toByteArray(),
            "Pair-Verify-Encrypt-Info".toByteArray(),
            32,
        )
        val decrypted = HapCrypto.chachaDecrypt(
            verifyKey,
            ByteArray(4) + "PV-Msg03".toByteArray(),
            byteArrayOf(),
            request.getValue(HapTlv.ENCRYPTED_DATA),
        )
        val identity = Tlv8Codec.decode(decrypted)
        val signature = identity.getValue(HapTlv.SIGNATURE)
        val info = pendingClientPublic + credentials.clientIdentifier + serverPublic
        controllerSignatureVerified = Ed25519PublicKeyParameters(
            credentials.clientPublicKey,
            0,
        ).verify(
            Ed25519.Algorithm.Ed25519,
            null,
            info,
            0,
            info.size,
            signature,
            0,
        )
        serverInputKey = HapCrypto.hkdfSha512(
            shared,
            byteArrayOf(),
            "ClientEncrypt-main".toByteArray(),
            32,
        )
        serverOutputKey = HapCrypto.hkdfSha512(
            shared,
            byteArrayOf(),
            "ServerEncrypt-main".toByteArray(),
            32,
        )
        return mapOf(
            "_pd" to Tlv8Codec.encode(mapOf(HapTlv.SEQUENCE to byteArrayOf(4))),
        )
    }
}
