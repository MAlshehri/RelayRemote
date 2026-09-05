package io.coreback.relayremote.remote

import io.coreback.relayremote.protocol.Capability
import io.coreback.relayremote.protocol.CapabilitySet
import io.coreback.relayremote.protocol.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteUiStateTest {
    @Test
    fun `playback skip defaults to 30 seconds`() {
        assertEquals(30, RemoteUiState().skipSeconds)
    }

    @Test
    fun `control is enabled only while connected and capability is live`() {
        val supported = RemoteUiState(
            connectionState = ConnectionState.Connected,
            capabilities = CapabilitySet(
                implemented = setOf(Capability.VOLUME),
                available = setOf(Capability.VOLUME),
            ),
        )

        assertTrue(supported.controlEnabled(Capability.VOLUME))
        assertFalse(supported.controlEnabled(Capability.MUTE))
        assertFalse(
            supported.copy(connectionState = ConnectionState.Reconnecting(1))
                .controlEnabled(Capability.VOLUME),
        )
    }
}
