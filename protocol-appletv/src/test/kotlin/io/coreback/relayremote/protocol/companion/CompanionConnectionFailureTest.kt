package io.coreback.relayremote.protocol.companion

import io.coreback.relayremote.protocol.ConnectionState
import io.coreback.relayremote.protocol.pairing.PairingAuthenticationException
import java.net.ConnectException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionConnectionFailureTest {
    @Test
    fun `rejected stored credential maps to authentication failed`() {
        assertEquals(
            ConnectionState.AuthenticationFailed,
            companionConnectionFailure(
                PairingAuthenticationException("Stored Apple TV credential was rejected"),
            ),
        )
    }

    @Test
    fun `transport failure remains retryable error`() {
        val state = companionConnectionFailure(IllegalArgumentException("Malformed endpoint"))

        assertTrue(state is ConnectionState.Error)
        assertEquals("Malformed endpoint", (state as ConnectionState.Error).message)
        assertTrue(state.retryable)
    }

    @Test
    fun `unreachable cached endpoint maps to offline`() {
        assertEquals(
            ConnectionState.Offline,
            companionConnectionFailure(ConnectException("Connection refused")),
        )
    }
}
