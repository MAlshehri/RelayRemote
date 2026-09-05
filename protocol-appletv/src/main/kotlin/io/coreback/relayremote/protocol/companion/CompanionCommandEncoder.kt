package io.coreback.relayremote.protocol.companion

import io.coreback.relayremote.protocol.Capability
import kotlin.math.abs
import kotlin.math.roundToInt

enum class HidButton(val code: Long) {
    UP(1),
    DOWN(2),
    LEFT(3),
    RIGHT(4),
    MENU(5),
    SELECT(6),
    HOME(7),
    VOLUME_UP(8),
    VOLUME_DOWN(9),
    SLEEP(12),
    WAKE(13),
    PLAY_PAUSE(14),
    CONTROL_CENTER(19),
}

internal data class CompanionMediaCommand(
    val capability: Capability,
    val payload: Map<String, Any?>,
)

object CompanionCommandEncoder {
    fun hid(button: HidButton, pressed: Boolean): Map<String, Any?> =
        mapOf(
            "_hBtS" to if (pressed) 1L else 2L,
            "_hidC" to button.code,
        )

    fun mediaCapabilities(flags: Int): Set<Capability> = buildSet {
        if (flags and (PLAY or PAUSE) != 0) add(Capability.PLAY_PAUSE)
        if (flags and NEXT != 0) add(Capability.NEXT)
        if (flags and PREVIOUS != 0) add(Capability.PREVIOUS)
        if (flags and SKIP_FORWARD != 0) add(Capability.SKIP_FORWARD)
        if (flags and SKIP_BACKWARD != 0) add(Capability.SKIP_BACKWARD)
        if (flags and VOLUME != 0) add(Capability.VOLUME)
    }

    fun touchPoint(normalizedX: Float, normalizedY: Float): Pair<Int, Int> =
        (normalizedX.coerceIn(0f, 1f) * 1000f).roundToInt() to
            (normalizedY.coerceIn(0f, 1f) * 1000f).roundToInt()

    internal fun skipBy(seconds: Int): CompanionMediaCommand {
        require(abs(seconds) in SUPPORTED_SKIP_SECONDS) {
            "Unsupported skip duration: $seconds"
        }
        return CompanionMediaCommand(
            capability = if (seconds > 0) Capability.SKIP_FORWARD else Capability.SKIP_BACKWARD,
            payload = mapOf("_mcc" to 7L, "_skpS" to seconds.toDouble()),
        )
    }

    private const val PLAY = 0x0001
    private const val PAUSE = 0x0002
    private const val NEXT = 0x0004
    private const val PREVIOUS = 0x0008
    private const val SKIP_FORWARD = 0x0200
    private const val SKIP_BACKWARD = 0x0400
    private const val VOLUME = 0x0100
    private val SUPPORTED_SKIP_SECONDS = setOf(10, 15, 30, 60)
}
