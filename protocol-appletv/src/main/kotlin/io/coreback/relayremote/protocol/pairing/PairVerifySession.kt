package io.coreback.relayremote.protocol.pairing

import io.coreback.relayremote.protocol.PairingCredentials
import io.coreback.relayremote.protocol.codec.CompanionFrameType
import io.coreback.relayremote.protocol.codec.Tlv8Codec
import io.coreback.relayremote.protocol.crypto.HapCrypto
import java.security.MessageDigest
import java.security.SecureRandom
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.math.ec.rfc8032.Ed25519

data class CompanionSessionKeys(
    val outputKey: ByteArray,
    val inputKey: ByteArray,
)

class PairVerifySession(
    private val exchange: CompanionAuthExchange,
    private val secureRandom: SecureRandom = SecureRandom(),
) {
    suspend fun verify(credentials: PairingCredentials): CompanionSessionKeys {
        validate(credentials)
        val clientCurve = X25519PrivateKeyParameters(secureRandom)
        val clientCurvePublic = clientCurve.generatePublicKey().encoded
        val startResponse = exchange.exchangeAuth(
            CompanionFrameType.PV_START,
            mapOf(
                PAIRING_DATA_KEY to Tlv8Codec.encode(
                    linkedMapOf(
                        HapTlv.SEQUENCE to byteArrayOf(1),
                        HapTlv.PUBLIC_KEY to clientCurvePublic,
                    ),
                ),
                "_auTy" to 4L,
            ),
        )
        val startTlv = pairingData(startResponse, 2)
        val serverCurvePublic = startTlv[HapTlv.PUBLIC_KEY]
            ?: throw PairingAuthenticationException("Apple TV omitted its session key")
        val encryptedAccessory = startTlv[HapTlv.ENCRYPTED_DATA]
            ?: throw PairingAuthenticationException("Apple TV omitted its verification data")
        if (serverCurvePublic.size != 32) {
            throw PairingAuthenticationException("Apple TV returned an invalid session key")
        }
        val shared = ByteArray(32)
        try {
            clientCurve.generateSecret(X25519PublicKeyParameters(serverCurvePublic, 0), shared, 0)
        } catch (error: Exception) {
            throw PairingAuthenticationException("Apple TV session key is invalid", error)
        }
        val verifyKey = HapCrypto.hkdfSha512(
            shared,
            PAIR_VERIFY_ENCRYPT_SALT,
            PAIR_VERIFY_ENCRYPT_INFO,
            32,
        )
        val accessoryIdentity = try {
            Tlv8Codec.decode(
                HapCrypto.chachaDecrypt(
                    verifyKey,
                    nonce("PV-Msg02"),
                    byteArrayOf(),
                    encryptedAccessory,
                ),
            )
        } catch (error: SecurityException) {
            throw PairingAuthenticationException("Apple TV verification message was altered", error)
        }
        val accessoryIdentifier = accessoryIdentity[HapTlv.IDENTIFIER]
            ?: throw PairingAuthenticationException("Apple TV omitted its identifier")
        if (!MessageDigest.isEqual(accessoryIdentifier, credentials.accessoryIdentifier)) {
            throw PairingAuthenticationException("Credential belongs to a different Apple TV")
        }
        val accessorySignature = accessoryIdentity[HapTlv.SIGNATURE]
            ?: throw PairingAuthenticationException("Apple TV omitted its signature")
        val accessoryInfo = serverCurvePublic + accessoryIdentifier + clientCurvePublic
        val accessorySignatureValid = try {
            Ed25519PublicKeyParameters(credentials.accessoryPublicKey, 0).verify(
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
        if (!accessorySignatureValid) {
            throw PairingAuthenticationException("Stored Apple TV credential was rejected")
        }

        val controllerInfo =
            clientCurvePublic + credentials.clientIdentifier + serverCurvePublic
        val controllerSignature = ByteArray(Ed25519.SIGNATURE_SIZE)
        Ed25519PrivateKeyParameters(credentials.clientPrivateKey, 0).sign(
            Ed25519.Algorithm.Ed25519,
            null,
            controllerInfo,
            0,
            controllerInfo.size,
            controllerSignature,
            0,
        )
        val controllerIdentity = Tlv8Codec.encode(
            linkedMapOf(
                HapTlv.IDENTIFIER to credentials.clientIdentifier,
                HapTlv.SIGNATURE to controllerSignature,
            ),
        )
        val encryptedController = HapCrypto.chachaEncrypt(
            verifyKey,
            nonce("PV-Msg03"),
            byteArrayOf(),
            controllerIdentity,
        )
        val finishResponse = exchange.exchangeAuth(
            CompanionFrameType.PV_NEXT,
            mapOf(
                PAIRING_DATA_KEY to Tlv8Codec.encode(
                    linkedMapOf(
                        HapTlv.SEQUENCE to byteArrayOf(3),
                        HapTlv.ENCRYPTED_DATA to encryptedController,
                    ),
                ),
            ),
        )
        pairingData(finishResponse, 4)

        val output = HapCrypto.hkdfSha512(
            shared,
            byteArrayOf(),
            CLIENT_ENCRYPT_INFO,
            32,
        )
        val input = HapCrypto.hkdfSha512(
            shared,
            byteArrayOf(),
            SERVER_ENCRYPT_INFO,
            32,
        )
        shared.fill(0)
        verifyKey.fill(0)
        return CompanionSessionKeys(output, input)
    }

    private fun pairingData(
        response: Map<String, Any?>,
        expectedSequence: Int,
    ): Map<Int, ByteArray> {
        val data = response[PAIRING_DATA_KEY] as? ByteArray
            ?: throw PairingAuthenticationException("Apple TV omitted pairing data")
        val tlv = Tlv8Codec.decode(data)
        tlv[HapTlv.ERROR]?.let {
            throw PairingAuthenticationException("Apple TV authentication error")
        }
        val sequence = tlv[HapTlv.SEQUENCE]?.firstOrNull()?.toInt()
        if (sequence != expectedSequence) {
            throw PairingAuthenticationException("Unexpected verification sequence $sequence")
        }
        return tlv
    }

    private fun validate(credentials: PairingCredentials) {
        require(credentials.clientIdentifier.isNotEmpty())
        require(credentials.accessoryIdentifier.isNotEmpty())
        require(credentials.clientPrivateKey.size == 32)
        require(credentials.clientPublicKey.size == 32)
        require(credentials.accessoryPublicKey.size == 32)
    }

    private fun nonce(value: String): ByteArray = ByteArray(4) + value.toByteArray()

    private companion object {
        const val PAIRING_DATA_KEY = "_pd"
        val PAIR_VERIFY_ENCRYPT_SALT = "Pair-Verify-Encrypt-Salt".toByteArray()
        val PAIR_VERIFY_ENCRYPT_INFO = "Pair-Verify-Encrypt-Info".toByteArray()
        val CLIENT_ENCRYPT_INFO = "ClientEncrypt-main".toByteArray()
        val SERVER_ENCRYPT_INFO = "ServerEncrypt-main".toByteArray()
    }
}
