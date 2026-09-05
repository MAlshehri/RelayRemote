package io.coreback.relayremote.protocol.crypto

import java.io.ByteArrayOutputStream
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object HapCrypto {
    private const val SHA512_LENGTH = 64

    fun hkdfSha512(
        inputKeyMaterial: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        outputLength: Int,
    ): ByteArray {
        require(outputLength in 1..(255 * SHA512_LENGTH))
        val effectiveSalt = if (salt.isEmpty()) ByteArray(SHA512_LENGTH) else salt
        val extract = Mac.getInstance("HmacSHA512")
        extract.init(SecretKeySpec(effectiveSalt, "HmacSHA512"))
        val pseudoRandomKey = extract.doFinal(inputKeyMaterial)
        try {
            val output = ByteArrayOutputStream(outputLength)
            var previous = ByteArray(0)
            var counter = 1
            while (output.size() < outputLength) {
                val expand = Mac.getInstance("HmacSHA512")
                expand.init(SecretKeySpec(pseudoRandomKey, "HmacSHA512"))
                expand.update(previous)
                expand.update(info)
                expand.update(counter.toByte())
                previous.fill(0)
                previous = expand.doFinal()
                output.write(previous, 0, minOf(previous.size, outputLength - output.size()))
                counter++
            }
            previous.fill(0)
            return output.toByteArray()
        } finally {
            pseudoRandomKey.fill(0)
        }
    }

    fun chachaEncrypt(
        key: ByteArray,
        nonce: ByteArray,
        aad: ByteArray,
        plaintext: ByteArray,
    ): ByteArray = chacha(Cipher.ENCRYPT_MODE, key, nonce, aad, plaintext)

    fun chachaDecrypt(
        key: ByteArray,
        nonce: ByteArray,
        aad: ByteArray,
        ciphertextAndTag: ByteArray,
    ): ByteArray = try {
        chacha(Cipher.DECRYPT_MODE, key, nonce, aad, ciphertextAndTag)
    } catch (error: GeneralSecurityException) {
        throw SecurityException("Companion message authentication failed", error)
    }

    private fun chacha(
        mode: Int,
        key: ByteArray,
        nonce: ByteArray,
        aad: ByteArray,
        input: ByteArray,
    ): ByteArray {
        require(key.size == 32) { "ChaCha20-Poly1305 requires a 256-bit key" }
        require(nonce.size == 12) { "ChaCha20-Poly1305 requires a 96-bit nonce" }
        val cipher = Cipher.getInstance("ChaCha20-Poly1305")
        cipher.init(mode, SecretKeySpec(key, "ChaCha20"), IvParameterSpec(nonce))
        cipher.updateAAD(aad)
        return cipher.doFinal(input)
    }
}
