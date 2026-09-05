package io.coreback.relayremote.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CredentialCipherAndroidTest {
    @Test
    fun credentialEnvelopeRoundTripsWithAndroidKeystoreKey() {
        val cipher = CredentialCipher(AndroidKeystoreKeyProvider())
        val plaintext = "pairing-credential-fixture".toByteArray()

        val envelope = cipher.encrypt("android-keystore-regression", plaintext)

        assertArrayEquals(
            plaintext,
            cipher.decrypt("android-keystore-regression", envelope),
        )
    }
}
