package io.coreback.relayremote.data

import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

fun interface SecretKeyProvider {
    fun getOrCreate(): SecretKey
}

class CredentialCipher(
    private val keyProvider: SecretKeyProvider,
) {
    fun encrypt(deviceIdentifier: String, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyProvider.getOrCreate())
        val iv = cipher.iv
        check(iv.size == IV_SIZE) { "AES-GCM provider returned an invalid IV" }
        cipher.updateAAD(deviceIdentifier.toByteArray(Charsets.UTF_8))
        val ciphertext = cipher.doFinal(plaintext)
        return byteArrayOf(FORMAT_VERSION) + iv + ciphertext
    }

    fun decrypt(deviceIdentifier: String, envelope: ByteArray): ByteArray {
        if (envelope.size < 1 + IV_SIZE + TAG_BITS / 8 || envelope[0] != FORMAT_VERSION) {
            throw SecurityException("Invalid credential envelope")
        }
        val iv = envelope.copyOfRange(1, 1 + IV_SIZE)
        val ciphertext = envelope.copyOfRange(1 + IV_SIZE, envelope.size)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                keyProvider.getOrCreate(),
                GCMParameterSpec(TAG_BITS, iv),
            )
            cipher.updateAAD(deviceIdentifier.toByteArray(Charsets.UTF_8))
            cipher.doFinal(ciphertext)
        } catch (error: GeneralSecurityException) {
            throw SecurityException("Credential authentication failed", error)
        }
    }

    private companion object {
        const val FORMAT_VERSION: Byte = 1
        const val IV_SIZE = 12
        const val TAG_BITS = 128
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
