package io.coreback.relayremote.protocol

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityTest {
    @Test
    fun `command support requires both implementation and device capability`() {
        val capabilities = CapabilitySet(
            implemented = setOf(Capability.NAVIGATION, Capability.PLAY_PAUSE),
            available = setOf(Capability.NAVIGATION, Capability.VOLUME),
        )

        assertTrue(capabilities.supports(Capability.NAVIGATION))
        assertFalse(capabilities.supports(Capability.PLAY_PAUSE))
        assertFalse(capabilities.supports(Capability.VOLUME))
    }
}
