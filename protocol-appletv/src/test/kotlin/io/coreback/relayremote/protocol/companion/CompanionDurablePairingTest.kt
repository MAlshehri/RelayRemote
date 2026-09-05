package io.coreback.relayremote.protocol.companion

import io.coreback.relayremote.protocol.ConnectionState
import io.coreback.relayremote.protocol.PairingCredentials
import java.net.ConnectException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionDurablePairingTest {
    @Test
    fun `saved pairing completes when immediate authenticated reconnect is offline`() = runTest {
        val credentials = credentials()
        val events = mutableListOf<String>()
        var failureState: ConnectionState? = null

        val result = completePairingDurably(
            credentials = credentials,
            persist = { events += "persist" },
            onPersisted = { events += "cleanup" },
            connect = {
                events += "connect"
                throw ConnectException("Connection refused")
            },
            onConnectionFailure = { failureState = it },
        )

        assertSame(credentials, result)
        assertEquals(listOf("persist", "cleanup", "connect"), events)
        assertEquals(ConnectionState.Offline, failureState)
    }

    @Test
    fun `credential persistence failure remains a pairing failure`() = runTest {
        var cleanedUp = false
        var connected = false

        val failure = runCatching {
            completePairingDurably(
                credentials = credentials(),
                persist = { error("Storage unavailable") },
                onPersisted = { cleanedUp = true },
                connect = { connected = true },
                onConnectionFailure = {},
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals("Storage unavailable", failure?.message)
        assertTrue(!cleanedUp)
        assertTrue(!connected)
    }

    private fun credentials() = PairingCredentials(
        clientIdentifier = byteArrayOf(1),
        clientPrivateKey = ByteArray(32),
        clientPublicKey = ByteArray(32),
        accessoryIdentifier = byteArrayOf(2),
        accessoryPublicKey = ByteArray(32),
    )
}
