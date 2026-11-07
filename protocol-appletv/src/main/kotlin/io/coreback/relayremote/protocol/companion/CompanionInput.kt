package io.coreback.relayremote.protocol.companion

import io.coreback.relayremote.protocol.TouchGesture
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal interface CompanionCommandChannel {
    suspend fun exchange(identifier: String, content: Map<String, Any?>): Map<String, Any?>
    suspend fun sendEvent(identifier: String, content: Map<String, Any?>)
}

/**
 * Keeps a slow network from building a backlog of stale swipe positions: only the newest
 * position waits in the queue, and the queued action reads it when its turn comes.
 * Each swipe has its own slot, so a position can never leak into another swipe.
 */
internal class TouchMoveCoalescer {
    @Volatile private var slot = AtomicReference<TouchGesture.Moved?>(null)

    fun startSwipe() {
        slot = AtomicReference(null)
    }

    /** Stores [move]. Returns the read to enqueue, or null if a queued read will pick it up. */
    fun offer(move: TouchGesture.Moved): PendingMove? {
        val current = slot
        return if (current.getAndSet(move) == null) PendingMove(current) else null
    }

    class PendingMove(private val slot: AtomicReference<TouchGesture.Moved?>) {
        fun take(): TouchGesture.Moved? = slot.getAndSet(null)
    }
}

/**
 * Button, touch, and mute sequences on the Companion channel.
 *
 * Every method must run on the client's ordered command queue, so a press and its
 * release cannot be reordered, and a held button delays later commands until it lifts.
 */
internal class CompanionInput(
    private val channel: CompanionCommandChannel,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private val _muted = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

    private var touchEpochNanos = 0L
    private var selectHeldSinceNanos: Long? = null
    private var volumeBeforeMute: Double? = null

    fun startTouchClock() {
        touchEpochNanos = nanoTime()
    }

    /** Forgets session state. The Apple TV drops held buttons with the closed connection. */
    fun resetSession() {
        selectHeldSinceNanos = null
        volumeBeforeMute = null
        _muted.value = false
    }

    suspend fun press(button: HidButton) {
        setPressed(button, true)
        setPressed(button, false)
    }

    /** Matches pyatv's Hold action: press, wait long enough to count as a hold, release. */
    suspend fun pressAndHold(button: HidButton) {
        setPressed(button, true)
        try {
            delay(MINIMUM_HOLD_MILLIS)
        } finally {
            setPressed(button, false)
        }
    }

    suspend fun beginSelectHold() {
        if (selectHeldSinceNanos != null) return
        setPressed(HidButton.SELECT, true)
        selectHeldSinceNanos = nanoTime()
    }

    /**
     * Keeps Select down for at least [MINIMUM_HOLD_MILLIS], because the touchpad recognizes
     * a hold sooner than tvOS does. An early lift still opens the menu instead of selecting.
     */
    suspend fun endSelectHold() {
        val heldSince = selectHeldSinceNanos ?: return
        val heldMillis = (nanoTime() - heldSince) / NANOS_PER_MILLI
        delay((MINIMUM_HOLD_MILLIS - heldMillis).coerceAtLeast(0))
        selectHeldSinceNanos = null
        setPressed(HidButton.SELECT, false)
        sendTouchPhase(TOUCH_EDGE, TOUCH_EDGE, TOUCH_CLICK)
    }

    suspend fun touch(x: Float, y: Float, phase: Long) {
        val point = CompanionCommandEncoder.touchPoint(x, y)
        sendTouchPhase(point.first.toLong(), point.second.toLong(), phase)
    }

    suspend fun toggleMute() {
        if (_muted.value) {
            val restored = volumeBeforeMute ?: UNMUTE_FALLBACK_LEVEL
            channel.exchange("_mcc", CompanionCommandEncoder.setVolume(restored))
            volumeBeforeMute = null
            _muted.value = false
            return
        }
        val response = channel.exchange("_mcc", CompanionCommandEncoder.getVolume())
        val current = (response["_vol"] as? Number)?.toDouble()
            ?: throw IllegalStateException("Apple TV did not report its volume.")
        if (current <= 0.0) {
            // Already silent, for example muted before this session began: nothing to save.
            channel.exchange("_mcc", CompanionCommandEncoder.setVolume(UNMUTE_FALLBACK_LEVEL))
            return
        }
        channel.exchange("_mcc", CompanionCommandEncoder.setVolume(0.0))
        volumeBeforeMute = current
        _muted.value = true
    }

    /** Changing the level by hand ends the mute, so the old level must not come back later. */
    fun volumeChangedByUser() {
        volumeBeforeMute = null
        _muted.value = false
    }

    private suspend fun setPressed(button: HidButton, pressed: Boolean) {
        channel.exchange("_hidC", CompanionCommandEncoder.hid(button, pressed))
    }

    private suspend fun sendTouchPhase(x: Long, y: Long, phase: Long) {
        channel.sendEvent(
            "_hidT",
            mapOf(
                "_ns" to (nanoTime() - touchEpochNanos).coerceAtLeast(0),
                "_tFg" to 1L,
                "_cx" to x,
                "_tPh" to phase,
                "_cy" to y,
            ),
        )
    }

    companion object {
        const val TOUCH_PRESS = 1L
        const val TOUCH_HOLD = 3L
        const val TOUCH_RELEASE = 4L
        const val TOUCH_CLICK = 5L
        const val MINIMUM_HOLD_MILLIS = 1_000L
        private const val TOUCH_EDGE = 1_000L
        private const val UNMUTE_FALLBACK_LEVEL = 0.2
        private const val NANOS_PER_MILLI = 1_000_000L
    }
}
