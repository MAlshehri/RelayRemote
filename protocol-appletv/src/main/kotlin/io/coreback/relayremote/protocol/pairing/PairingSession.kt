package io.coreback.relayremote.protocol.pairing

import io.coreback.relayremote.protocol.PairingCredentials
import io.coreback.relayremote.protocol.codec.CompanionFrameType
import io.coreback.relayremote.protocol.codec.OpackCodec
import io.coreback.relayremote.protocol.codec.Tlv8Codec
import io.coreback.relayremote.protocol.crypto.HapCrypto
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import org.bouncycastle.crypto.agreement.srp.SRP6Client
import org.bouncycastle.crypto.agreement.srp.SRP6StandardGroups
import org.bouncycastle.crypto.digests.SHA512Digest
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.math.ec.rfc8032.Ed25519

object HapTlv {
    const val METHOD = 0x00
    const val IDENTIFIER = 0x01
    const val SALT = 0x02
    const val PUBLIC_KEY = 0x03
    const val PROOF = 0x04
    const val ENCRYPTED_DATA = 0x05
    const val SEQUENCE = 0x06
    const val ERROR = 0x07
    const val SIGNATURE = 0x0A
    const val NAME = 0x11
}

fun interface CompanionAuthExchange {
    suspend fun exchangeAuth(
        requestType: CompanionFrameType,
        content: Map<String, Any?>,
    ): Map<String, Any?>
}

class PairingAuthenticationException(message: String, cause: Throwable? = null) :
    SecurityException(message, cause)

class PairingSession(
    private val exchange: CompanionAuthExchange,
    private val displayName: String,
    private val secureRandom: SecureRandom = SecureRandom(),
    private val trace: (String) -> Unit = {},
) {
    private val clientIdentifier = UUID.randomUUID().toString().toByteArray()
    private val clientKey = Ed25519PrivateKeyParameters(secureRandom)
    private val clientPublicKey = clientKey.generatePublicKey().encoded
    private var accessorySalt: ByteArray? = null
    private var accessorySrpPublic: ByteArray? = null
    private var began = false

    suspend fun begin() {
        check(!began) { "Pairing has already started" }
        val response = exchange.exchangeAuth(
            CompanionFrameType.PS_START,
            mapOf(
                PAIRING_DATA_KEY to Tlv8Codec.encode(
                    linkedMapOf(
                        HapTlv.METHOD to byteArrayOf(0),
                        HapTlv.SEQUENCE to byteArrayOf(1),
                    ),
                ),
                "_pwTy" to 1L,
            ),
        )
        val tlv = pairingData(response, expectedSequence = 2)
        accessorySalt = tlv[HapTlv.SALT]
            ?: throw PairingAuthenticationException("Apple TV omitted the SRP salt")
        accessorySrpPublic = tlv[HapTlv.PUBLIC_KEY]
            ?: throw PairingAuthenticationException("Apple TV omitted the SRP public key")
        trace(
            "M2 received: saltBytes=${accessorySalt?.size}, " +
                "serverPublicBytes=${accessorySrpPublic?.size}",
        )
        began = true
    }

    suspend fun finish(pin: String): PairingCredentials {
        require(PIN_PATTERN.matches(pin)) { "PIN must contain exactly four digits" }
        check(began) { "Pairing has not started" }
        val salt = accessorySalt ?: error("Missing SRP salt")
        val serverPublicBytes = accessorySrpPublic ?: error("Missing SRP public key")
        val group = SRP6StandardGroups.rfc5054_3072
        val client = SRP6Client().apply {
            init(group.n, group.g, SHA512Digest(), secureRandom)
        }
        val clientPublic = client.generateClientCredentials(
            salt,
            SRP_IDENTITY,
            pin.toByteArray(),
        )
        val serverPublic = BigInteger(1, serverPublicBytes)
        val secret = try {
            client.calculateSecret(serverPublic)
        } catch (error: Exception) {
            throw PairingAuthenticationException("Apple TV returned an invalid SRP key", error)
        }
        val sessionKey = sha512(unsigned(secret))
        val clientProof = hapProof(
            group.n,
            group.g,
            salt,
            clientPublic,
            serverPublic,
            sessionKey,
        )
        val encodedClientPublic = fixed(clientPublic, group.n)
        trace(
            "M3 sending: clientPublicBytes=${encodedClientPublic.size}, " +
                "clientProofBytes=${clientProof.size}",
        )

        val proofResponse = exchange.exchangeAuth(
            CompanionFrameType.PS_NEXT,
            mapOf(
                PAIRING_DATA_KEY to Tlv8Codec.encode(
                    linkedMapOf(
                        HapTlv.SEQUENCE to byteArrayOf(3),
                        HapTlv.PUBLIC_KEY to encodedClientPublic,
                        HapTlv.PROOF to clientProof,
                    ),
                ),
                "_pwTy" to 1L,
            ),
        )
        val proofTlv = pairingData(proofResponse, expectedSequence = 4)
        val serverProof = proofTlv[HapTlv.PROOF]
            ?: throw PairingAuthenticationException("Apple TV omitted its SRP proof")
        trace("M4 received: serverProofBytes=${serverProof.size}")
        val expectedServerProof = sha512(unsigned(clientPublic), clientProof, sessionKey)
        if (!MessageDigest.isEqual(serverProof, expectedServerProof)) {
            sessionKey.fill(0)
            trace("M4 rejected: server proof mismatch")
            throw PairingAuthenticationException("The Apple TV rejected the PIN")
        }
        trace("M4 verified: SRP proof accepted")

        val encryptionKey = HapCrypto.hkdfSha512(
            sessionKey,
            PAIR_SETUP_ENCRYPT_SALT,
            PAIR_SETUP_ENCRYPT_INFO,
            32,
        )
        val controllerX = HapCrypto.hkdfSha512(
            sessionKey,
            CONTROLLER_SIGN_SALT,
            CONTROLLER_SIGN_INFO,
            32,
        )
        val controllerInfo = controllerX + clientIdentifier + clientPublicKey
        val controllerSignature = ByteArray(Ed25519.SIGNATURE_SIZE)
        clientKey.sign(
            Ed25519.Algorithm.Ed25519,
            null,
            controllerInfo,
            0,
            controllerInfo.size,
            controllerSignature,
            0,
        )
        val controllerTlv = linkedMapOf(
            HapTlv.IDENTIFIER to clientIdentifier,
            HapTlv.PUBLIC_KEY to clientPublicKey,
            HapTlv.SIGNATURE to controllerSignature,
            HapTlv.NAME to OpackCodec.encode(mapOf("name" to displayName)),
        )
        val encryptedController = HapCrypto.chachaEncrypt(
            encryptionKey,
            nonce("PS-Msg05"),
            byteArrayOf(),
            Tlv8Codec.encode(controllerTlv),
        )
        trace(
            "M5 sending: identifierBytes=${clientIdentifier.size}, " +
                "publicKeyBytes=${clientPublicKey.size}, " +
                "signatureBytes=${controllerSignature.size}, " +
                "encryptedBytes=${encryptedController.size}",
        )

        val identityResponse = exchange.exchangeAuth(
            CompanionFrameType.PS_NEXT,
            mapOf(
                PAIRING_DATA_KEY to Tlv8Codec.encode(
                    linkedMapOf(
                        HapTlv.SEQUENCE to byteArrayOf(5),
                        HapTlv.ENCRYPTED_DATA to encryptedController,
                    ),
                ),
                "_pwTy" to 1L,
            ),
        )
        val identityTlv = pairingData(identityResponse, expectedSequence = 6)
        val encryptedAccessory = identityTlv[HapTlv.ENCRYPTED_DATA]
            ?: throw PairingAuthenticationException("Apple TV omitted its identity")
        trace("M6 received: encryptedBytes=${encryptedAccessory.size}")
        val accessory = try {
            Tlv8Codec.decode(
                HapCrypto.chachaDecrypt(
                    encryptionKey,
                    nonce("PS-Msg06"),
                    byteArrayOf(),
                    encryptedAccessory,
                ),
            )
        } catch (error: SecurityException) {
            throw PairingAuthenticationException("Apple TV identity was not authenticated", error)
        }
        val accessoryIdentifier = accessory[HapTlv.IDENTIFIER]
            ?: throw PairingAuthenticationException("Apple TV identity is incomplete")
        val accessoryPublicKey = accessory[HapTlv.PUBLIC_KEY]
            ?: throw PairingAuthenticationException("Apple TV public key is missing")
        val accessorySignature = accessory[HapTlv.SIGNATURE]
            ?: throw PairingAuthenticationException("Apple TV signature is missing")
        val accessoryX = HapCrypto.hkdfSha512(
            sessionKey,
            ACCESSORY_SIGN_SALT,
            ACCESSORY_SIGN_INFO,
            32,
        )
        val accessoryInfo = accessoryX + accessoryIdentifier + accessoryPublicKey
        val signatureValid = try {
            Ed25519PublicKeyParameters(accessoryPublicKey, 0).verify(
                Ed25519.Algorithm.Ed25519,
                null,
                accessoryInfo,
                0,
                accessoryInfo.size,
                accessorySignature,
                0,
            )
        } catch (_: Exception) {
            false
        }
        if (!signatureValid) {
            trace("M6 rejected: accessory signature invalid")
            throw PairingAuthenticationException("Apple TV identity signature is invalid")
        }
        trace(
            "M6 verified: identifierBytes=${accessoryIdentifier.size}, " +
                "publicKeyBytes=${accessoryPublicKey.size}",
        )

        sessionKey.fill(0)
        encryptionKey.fill(0)
        controllerX.fill(0)
        accessoryX.fill(0)
        return PairingCredentials(
            clientIdentifier = clientIdentifier.copyOf(),
            clientPrivateKey = clientKey.encoded,
            clientPublicKey = clientPublicKey.copyOf(),
            accessoryIdentifier = accessoryIdentifier,
            accessoryPublicKey = accessoryPublicKey,
        )
    }

    private fun pairingData(
        response: Map<String, Any?>,
        expectedSequence: Int,
    ): Map<Int, ByteArray> {
        val data = response[PAIRING_DATA_KEY] as? ByteArray
            ?: run {
                trace("M$expectedSequence rejected: pairing data missing")
                throw PairingAuthenticationException("Apple TV omitted pairing data")
            }
        val tlv = Tlv8Codec.decode(data)
        tlv[HapTlv.ERROR]?.let {
            trace(
                "M$expectedSequence rejected: Apple TV error=" +
                    "${it.firstOrNull()?.toInt()?.and(0xff)}",
            )
            throw PairingAuthenticationException("Apple TV pairing error ${it.firstOrNull()?.toInt()}")
        }
        val sequence = tlv[HapTlv.SEQUENCE]?.firstOrNull()?.toInt()
        if (sequence != expectedSequence) {
            trace("M$expectedSequence rejected: unexpected sequence=$sequence")
            throw PairingAuthenticationException("Unexpected pairing sequence $sequence")
        }
        return tlv
    }

    private fun hapProof(
        n: BigInteger,
        g: BigInteger,
        salt: ByteArray,
        clientPublic: BigInteger,
        serverPublic: BigInteger,
        key: ByteArray,
    ): ByteArray {
        val xor = BigInteger(1, sha512(unsigned(n)))
            .xor(BigInteger(1, sha512(unsigned(g))))
        return sha512(
            unsigned(xor),
            sha512(SRP_IDENTITY),
            salt,
            unsigned(clientPublic),
            unsigned(serverPublic),
            key,
        )
    }

    private fun sha512(vararg values: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-512").apply {
            values.forEach(::update)
        }.digest()

    private fun fixed(value: BigInteger, n: BigInteger): ByteArray =
        unsigned(value).let { raw ->
            ByteArray((n.bitLength() + 7) / 8).also {
                raw.copyInto(it, it.size - raw.size)
            }
        }

    private fun unsigned(value: BigInteger): ByteArray =
        value.toByteArray().let {
            if (it.size > 1 && it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it
        }

    private fun nonce(value: String): ByteArray = ByteArray(4) + value.toByteArray()

    private companion object {
        const val PAIRING_DATA_KEY = "_pd"
        val PIN_PATTERN = Regex("\\d{4}")
        val SRP_IDENTITY = "Pair-Setup".toByteArray()
        val PAIR_SETUP_ENCRYPT_SALT = "Pair-Setup-Encrypt-Salt".toByteArray()
        val PAIR_SETUP_ENCRYPT_INFO = "Pair-Setup-Encrypt-Info".toByteArray()
        val CONTROLLER_SIGN_SALT = "Pair-Setup-Controller-Sign-Salt".toByteArray()
        val CONTROLLER_SIGN_INFO = "Pair-Setup-Controller-Sign-Info".toByteArray()
        val ACCESSORY_SIGN_SALT = "Pair-Setup-Accessory-Sign-Salt".toByteArray()
        val ACCESSORY_SIGN_INFO = "Pair-Setup-Accessory-Sign-Info".toByteArray()
    }
}
