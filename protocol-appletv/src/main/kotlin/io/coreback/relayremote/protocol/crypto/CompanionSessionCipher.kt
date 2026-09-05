package io.coreback.relayremote.protocol.crypto

import java.util.concurrent.atomic.AtomicLong

class CompanionSessionCipher(
    private val outputKey: ByteArray,
    private val inputKey: ByteArray,
) {
    private val outputCounter = AtomicLong(0)
    private val inputCounter = AtomicLong(0)

    init {
        require(outputKey.size == 32)
        require(inputKey.size == 32)
    }

    fun encrypt(aad: ByteArray, plaintext: ByteArray): ByteArray =
        HapCrypto.chachaEncrypt(outputKey, nonce(outputCounter), aad, plaintext)

    fun decrypt(aad: ByteArray, ciphertextAndTag: ByteArray): ByteArray =
        HapCrypto.chachaDecrypt(inputKey, nonce(inputCounter), aad, ciphertextAndTag)

    fun destroy() {
        outputKey.fill(0)
        inputKey.fill(0)
    }

    private fun nonce(counter: AtomicLong): ByteArray {
        val value = counter.getAndIncrement()
        if (value < 0) throw SecurityException("Companion nonce counter exhausted")
        return ByteArray(12) { index ->
            if (index < Long.SIZE_BYTES) (value ushr (index * 8)).toByte() else 0
        }
    }
}
