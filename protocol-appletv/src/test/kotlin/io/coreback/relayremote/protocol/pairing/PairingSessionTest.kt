package io.coreback.relayremote.protocol.pairing

import io.coreback.relayremote.protocol.codec.CompanionFrameType
import io.coreback.relayremote.protocol.codec.Tlv8Codec
import io.coreback.relayremote.protocol.crypto.HapCrypto
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import org.bouncycastle.crypto.agreement.srp.SRP6Server
import org.bouncycastle.crypto.agreement.srp.SRP6StandardGroups
import org.bouncycastle.crypto.agreement.srp.SRP6VerifierGenerator
import org.bouncycastle.crypto.digests.SHA512Digest
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingSessionTest {
    @Test
    fun `pair setup authenticates both controller and accessory`() = runTest {
        val fake = FakeAppleTvPairingExchange("1234")
        val session = PairingSession(fake, "Relay Remote")

        session.begin()
        val credentials = session.finish("1234")

        assertEquals(3, fake.requests.size)
        assertEquals(CompanionFrameType.PS_START, fake.requests[0].first)
        assertArrayEquals(fake.accessoryIdentifier, credentials.accessoryIdentifier)
        assertArrayEquals(fake.accessoryPublicKey, credentials.accessoryPublicKey)
        assertTrue(credentials.clientIdentifier.isNotEmpty())
        assertEquals(32, credentials.clientPrivateKey.size)
        assertEquals(32, credentials.clientPublicKey.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `pin must contain exactly four digits`() = runTest {
        PairingSession(FakeAppleTvPairingExchange("1234"), "Relay Remote").apply {
            begin()
            finish("12ab")
        }
    }
}

private class FakeAppleTvPairingExchange(
    private val pin: String,
) : CompanionAuthExchange {
    val requests = mutableListOf<Pair<CompanionFrameType, Map<String, Any?>>>()
    val accessoryIdentifier = "Accessory-01".toByteArray()
    private val accessoryKey = Ed25519PrivateKeyParameters(SecureRandom())
    val accessoryPublicKey = accessoryKey.generatePublicKey().encoded
    private val group = SRP6StandardGroups.rfc5054_3072
    private val salt = ByteArray(16) { (it + 1).toByte() }
    private val verifier = SRP6VerifierGenerator().apply {
        init(group.n, group.g, SHA512Digest())
    }.generateVerifier(salt, IDENTITY, pin.toByteArray())
    private val server = SRP6Server().apply {
        init(group.n, group.g, verifier, SHA512Digest(), SecureRandom())
    }
    private val serverPublic = server.generateServerCredentials()
    private lateinit var sessionKey: ByteArray
    private lateinit var clientPublic: BigInteger
    private lateinit var clientProof: ByteArray

    override suspend fun exchangeAuth(
        requestType: CompanionFrameType,
        content: Map<String, Any?>,
    ): Map<String, Any?> {
        requests += requestType to content
        return when (requests.size) {
            1 -> response(
                mapOf(
                    HapTlv.SEQUENCE to byteArrayOf(2),
                    HapTlv.SALT to salt,
                    HapTlv.PUBLIC_KEY to fixed(serverPublic),
                ),
            )
            2 -> handleProof(content)
            3 -> handleControllerIdentity(content)
            else -> error("Unexpected pairing request")
        }
    }

    private fun handleProof(content: Map<String, Any?>): Map<String, Any?> {
        val tlv = requestTlv(content)
        clientPublic = BigInteger(1, tlv.getValue(HapTlv.PUBLIC_KEY))
        clientProof = tlv.getValue(HapTlv.PROOF)
        val secret = server.calculateSecret(clientPublic)
        sessionKey = sha512(unsigned(secret))
        assertArrayEquals(
            hapProof(group.n, group.g, salt, clientPublic, serverPublic, sessionKey),
            clientProof,
        )
        val serverProof = sha512(unsigned(clientPublic), clientProof, sessionKey)
        return response(
            mapOf(
                HapTlv.SEQUENCE to byteArrayOf(4),
                HapTlv.PROOF to serverProof,
            ),
        )
    }

    private fun handleControllerIdentity(content: Map<String, Any?>): Map<String, Any?> {
        val tlv = requestTlv(content)
        val encryptionKey = HapCrypto.hkdfSha512(
            sessionKey,
            PAIR_SETUP_ENCRYPT_SALT,
            PAIR_SETUP_ENCRYPT_INFO,
            32,
        )
        val decrypted = HapCrypto.chachaDecrypt(
            encryptionKey,
            nonce("PS-Msg05"),
            byteArrayOf(),
            tlv.getValue(HapTlv.ENCRYPTED_DATA),
        )
        val controller = Tlv8Codec.decode(decrypted)
        val controllerId = controller.getValue(HapTlv.IDENTIFIER)
        val controllerPublic = controller.getValue(HapTlv.PUBLIC_KEY)
        val controllerSignature = controller.getValue(HapTlv.SIGNATURE)
        val controllerX = HapCrypto.hkdfSha512(
            sessionKey,
            CONTROLLER_SIGN_SALT,
            CONTROLLER_SIGN_INFO,
            32,
        )
        assertTrue(
            Ed25519PublicKeyParameters(controllerPublic, 0).verify(
                0,
                null,
                controllerX + controllerId + controllerPublic,
                0,
                (controllerX + controllerId + controllerPublic).size,
                controllerSignature,
                0,
            ),
        )

        val accessoryX = HapCrypto.hkdfSha512(
            sessionKey,
            ACCESSORY_SIGN_SALT,
            ACCESSORY_SIGN_INFO,
            32,
        )
        val accessoryInfo = accessoryX + accessoryIdentifier + accessoryPublicKey
        val signature = ByteArray(64)
        accessoryKey.sign(0, null, accessoryInfo, 0, accessoryInfo.size, signature, 0)
        val responseTlv = Tlv8Codec.encode(
            linkedMapOf(
                HapTlv.IDENTIFIER to accessoryIdentifier,
                HapTlv.PUBLIC_KEY to accessoryPublicKey,
                HapTlv.SIGNATURE to signature,
            ),
        )
        val encrypted = HapCrypto.chachaEncrypt(
            encryptionKey,
            nonce("PS-Msg06"),
            byteArrayOf(),
            responseTlv,
        )
        return response(
            mapOf(
                HapTlv.SEQUENCE to byteArrayOf(6),
                HapTlv.ENCRYPTED_DATA to encrypted,
            ),
        )
    }

    private fun requestTlv(content: Map<String, Any?>): Map<Int, ByteArray> =
        Tlv8Codec.decode(content.getValue("_pd") as ByteArray)

    private fun response(values: Map<Int, ByteArray>): Map<String, Any?> =
        mapOf("_pd" to Tlv8Codec.encode(values))

    private fun hapProof(
        n: BigInteger,
        g: BigInteger,
        salt: ByteArray,
        a: BigInteger,
        b: BigInteger,
        key: ByteArray,
    ): ByteArray {
        val xor = BigInteger(1, sha512(unsigned(n)))
            .xor(BigInteger(1, sha512(unsigned(g))))
        return sha512(
            unsigned(xor),
            sha512(IDENTITY),
            salt,
            unsigned(a),
            unsigned(b),
            key,
        )
    }

    private fun sha512(vararg values: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-512").apply {
            values.forEach(::update)
        }.digest()

    private fun fixed(value: BigInteger): ByteArray =
        unsigned(value).let { raw ->
            ByteArray((group.n.bitLength() + 7) / 8).also {
                raw.copyInto(it, it.size - raw.size)
            }
        }

    private fun unsigned(value: BigInteger): ByteArray =
        value.toByteArray().let { if (it.size > 1 && it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it }

    private fun nonce(value: String): ByteArray = ByteArray(4) + value.toByteArray()

    private companion object {
        val IDENTITY = "Pair-Setup".toByteArray()
        val PAIR_SETUP_ENCRYPT_SALT = "Pair-Setup-Encrypt-Salt".toByteArray()
        val PAIR_SETUP_ENCRYPT_INFO = "Pair-Setup-Encrypt-Info".toByteArray()
        val CONTROLLER_SIGN_SALT = "Pair-Setup-Controller-Sign-Salt".toByteArray()
        val CONTROLLER_SIGN_INFO = "Pair-Setup-Controller-Sign-Info".toByteArray()
        val ACCESSORY_SIGN_SALT = "Pair-Setup-Accessory-Sign-Salt".toByteArray()
        val ACCESSORY_SIGN_INFO = "Pair-Setup-Accessory-Sign-Info".toByteArray()
    }
}
