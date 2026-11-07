package io.coreback.relayremote.protocol.companion

import io.coreback.relayremote.protocol.Capability
import io.coreback.relayremote.protocol.ScanDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionCommandEncoderTest {
    @Test
    fun `hid command uses pyatv referenced button and press state values`() {
        assertEquals(
            mapOf("_hBtS" to 1L, "_hidC" to 7L),
            CompanionCommandEncoder.hid(HidButton.HOME, pressed = true),
        )
        assertEquals(
            mapOf("_hBtS" to 2L, "_hidC" to 5L),
            CompanionCommandEncoder.hid(HidButton.MENU, pressed = false),
        )
    }

    @Test
    fun `media control flags only expose implemented live capabilities`() {
        val capabilities = CompanionCommandEncoder.mediaCapabilities(
            flags = 0x0100 or 0x0001 or 0x0200,
        )

        assertTrue(Capability.PLAY_PAUSE in capabilities)
        assertTrue(Capability.VOLUME in capabilities)
        assertTrue(Capability.SKIP_FORWARD in capabilities)
        assertFalse(Capability.SKIP_BACKWARD in capabilities)
        assertTrue(Capability.MUTE in capabilities)
        assertFalse(Capability.NEXT in capabilities)
    }

    @Test
    fun `pyatv skip bits enable each direction and legacy bits do not`() {
        assertEquals(
            setOf(Capability.SKIP_FORWARD),
            CompanionCommandEncoder.mediaCapabilities(0x0200),
        )
        assertEquals(
            setOf(Capability.SKIP_BACKWARD),
            CompanionCommandEncoder.mediaCapabilities(0x0400),
        )
        assertFalse(
            Capability.SKIP_FORWARD in CompanionCommandEncoder.mediaCapabilities(0x0020),
        )
        assertFalse(
            Capability.SKIP_BACKWARD in CompanionCommandEncoder.mediaCapabilities(0x0040),
        )
    }

    @Test
    fun `pyatv scan flags enable each direction`() {
        assertEquals(setOf(Capability.FAST_FORWARD), CompanionCommandEncoder.mediaCapabilities(0x0010))
        assertEquals(setOf(Capability.REWIND), CompanionCommandEncoder.mediaCapabilities(0x0020))
    }

    @Test
    fun `new buttons use pyatv referenced hid codes`() {
        assertEquals(11L, HidButton.SCREENSAVER.code)
        assertEquals(15L, HidButton.CHANNEL_UP.code)
        assertEquals(16L, HidButton.CHANNEL_DOWN.code)
        assertEquals(17L, HidButton.GUIDE.code)
        assertEquals(19L, HidButton.CONTROL_CENTER.code)
    }

    @Test
    fun `scan pairs each start with its own stop command`() {
        assertEquals(
            mapOf("_mcc" to 8L),
            CompanionCommandEncoder.scan(ScanDirection.FAST_FORWARD, active = true),
        )
        assertEquals(
            mapOf("_mcc" to 9L),
            CompanionCommandEncoder.scan(ScanDirection.FAST_FORWARD, active = false),
        )
        assertEquals(
            mapOf("_mcc" to 10L),
            CompanionCommandEncoder.scan(ScanDirection.REWIND, active = true),
        )
        assertEquals(
            mapOf("_mcc" to 11L),
            CompanionCommandEncoder.scan(ScanDirection.REWIND, active = false),
        )
    }

    @Test
    fun `volume and account payloads use pyatv keys`() {
        assertEquals(mapOf("_mcc" to 5L), CompanionCommandEncoder.getVolume())
        assertEquals(mapOf("_mcc" to 6L, "_vol" to 1.0), CompanionCommandEncoder.setVolume(1.4))
        assertEquals(
            mapOf("SwitchAccountID" to "abc"),
            CompanionCommandEncoder.switchAccount("abc"),
        )
    }

    @Test
    fun `touch coordinates clamp to companion coordinate space`() {
        assertEquals(0 to 1000, CompanionCommandEncoder.touchPoint(-0.5f, 1.5f))
        assertEquals(250 to 750, CompanionCommandEncoder.touchPoint(0.25f, 0.75f))
    }

    @Test
    fun `skip command preserves signed duration and direction capability`() {
        val forward = CompanionCommandEncoder.skipBy(30)
        val backward = CompanionCommandEncoder.skipBy(-30)

        assertEquals(Capability.SKIP_FORWARD, forward.capability)
        assertEquals(mapOf("_mcc" to 7L, "_skpS" to 30.0), forward.payload)
        assertEquals(Capability.SKIP_BACKWARD, backward.capability)
        assertEquals(mapOf("_mcc" to 7L, "_skpS" to -30.0), backward.payload)
    }

    @Test
    fun `skip command rejects zero and unsupported duration`() {
        assertTrue(
            runCatching { CompanionCommandEncoder.skipBy(0) }
                .exceptionOrNull() is IllegalArgumentException,
        )
        assertTrue(
            runCatching { CompanionCommandEncoder.skipBy(45) }
                .exceptionOrNull() is IllegalArgumentException,
        )
    }
}
