package io.coreback.relayremote.protocol.companion

import io.coreback.relayremote.protocol.TouchGesture
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CompanionInputTest {
    private class RecordingChannel(
        var volume: Double = 0.6,
    ) : CompanionCommandChannel {
        val sent = mutableListOf<Pair<String, Map<String, Any?>>>()

        override suspend fun exchange(
            identifier: String,
            content: Map<String, Any?>,
        ): Map<String, Any?> {
            sent += identifier to content
            if (identifier == "_mcc" && content["_mcc"] == 5L) return mapOf("_vol" to volume)
            return emptyMap()
        }

        override suspend fun sendEvent(identifier: String, content: Map<String, Any?>) {
            sent += identifier to content
        }

        val buttons: List<Pair<Long, Long>>
            get() = sent.filter { it.first == "_hidC" }
                .map { (_, content) -> content["_hidC"] as Long to content["_hBtS"] as Long }

        val touchPhases: List<Long>
            get() = sent.filter { it.first == "_hidT" }.map { it.second["_tPh"] as Long }
    }

    private fun TestScope.input(channel: CompanionCommandChannel) =
        CompanionInput(channel, nanoTime = { currentTime * 1_000_000 })

    @Test
    fun `long hold presses select once and releases when the finger lifts`() = runTest {
        val channel = RecordingChannel()
        val input = input(channel)

        input.beginSelectHold()
        assertEquals(listOf(SELECT to DOWN), channel.buttons)

        advanceTimeBy(1_500)
        val started = currentTime
        input.endSelectHold()

        assertEquals(started, currentTime)
        assertEquals(listOf(SELECT to DOWN, SELECT to UP), channel.buttons)
        assertEquals(listOf(CompanionInput.TOUCH_CLICK), channel.touchPhases)
    }

    @Test
    fun `early lift keeps select down long enough for tvOS to see a hold`() = runTest {
        val channel = RecordingChannel()
        val input = input(channel)

        input.beginSelectHold()
        advanceTimeBy(200)
        val release = launch { input.endSelectHold() }

        advanceTimeBy(700)
        assertEquals(listOf(SELECT to DOWN), channel.buttons)

        advanceUntilIdle()
        release.join()
        assertEquals(listOf(SELECT to DOWN, SELECT to UP), channel.buttons)
    }

    @Test
    fun `repeated hold start and stray hold end send nothing extra`() = runTest {
        val channel = RecordingChannel()
        val input = input(channel)

        input.endSelectHold()
        assertTrue(channel.sent.isEmpty())

        input.beginSelectHold()
        input.beginSelectHold()
        assertEquals(listOf(SELECT to DOWN), channel.buttons)
    }

    @Test
    fun `reset forgets a held select so a new session cannot release it`() = runTest {
        val channel = RecordingChannel()
        val input = input(channel)

        input.beginSelectHold()
        input.resetSession()
        input.endSelectHold()

        assertEquals(listOf(SELECT to DOWN), channel.buttons)
    }

    @Test
    fun `press and hold waits between press and release`() = runTest {
        val channel = RecordingChannel()
        val input = input(channel)

        input.pressAndHold(HidButton.MENU)

        assertEquals(CompanionInput.MINIMUM_HOLD_MILLIS, currentTime)
        assertEquals(listOf(MENU to DOWN, MENU to UP), channel.buttons)
    }

    @Test
    fun `touch uses companion coordinates and the requested phase`() = runTest {
        val channel = RecordingChannel()
        val input = input(channel)

        input.startTouchClock()
        advanceTimeBy(5)
        input.touch(0.25f, 1.5f, CompanionInput.TOUCH_PRESS)

        assertEquals(
            mapOf(
                "_ns" to 5_000_000L,
                "_tFg" to 1L,
                "_cx" to 250L,
                "_tPh" to 1L,
                "_cy" to 1_000L,
            ),
            channel.sent.single().second,
        )
    }

    @Test
    fun `mute saves the level and unmute restores it`() = runTest {
        val channel = RecordingChannel(volume = 0.6)
        val input = input(channel)

        input.toggleMute()
        assertTrue(input.muted.value)
        assertEquals(mapOf("_mcc" to 6L, "_vol" to 0.0), channel.sent.last().second)

        input.toggleMute()
        assertFalse(input.muted.value)
        assertEquals(mapOf("_mcc" to 6L, "_vol" to 0.6), channel.sent.last().second)
    }

    @Test
    fun `manual volume change ends mute without restoring the old level later`() = runTest {
        val channel = RecordingChannel(volume = 0.6)
        val input = input(channel)

        input.toggleMute()
        input.volumeChangedByUser()
        assertFalse(input.muted.value)

        channel.volume = 0.1
        input.toggleMute()
        input.toggleMute()
        assertEquals(mapOf("_mcc" to 6L, "_vol" to 0.1), channel.sent.last().second)
    }

    @Test
    fun `pressing mute on a silent tv restores an audible level`() = runTest {
        val channel = RecordingChannel(volume = 0.0)
        val input = input(channel)

        input.toggleMute()

        assertFalse(input.muted.value)
        assertEquals(mapOf("_mcc" to 6L, "_vol" to 0.2), channel.sent.last().second)
    }

    @Test
    fun `missing volume report fails without claiming to be muted`() = runTest {
        val channel = object : CompanionCommandChannel {
            override suspend fun exchange(identifier: String, content: Map<String, Any?>) =
                emptyMap<String, Any?>()

            override suspend fun sendEvent(identifier: String, content: Map<String, Any?>) = Unit
        }
        val input = input(channel)

        assertTrue(runCatching { input.toggleMute() }.isFailure)
        assertFalse(input.muted.value)
    }

    @Test
    fun `only the newest position of a swipe waits in the queue`() {
        val coalescer = TouchMoveCoalescer()
        val first = TouchGesture.Moved(0.1f, 0.1f)
        val newest = TouchGesture.Moved(0.2f, 0.2f)

        val pending = coalescer.offer(first)
        assertEquals(null, coalescer.offer(newest))

        assertEquals(newest, pending?.take())
        assertEquals(null, pending?.take())
    }

    @Test
    fun `a new swipe cannot change a position queued by the previous swipe`() {
        val coalescer = TouchMoveCoalescer()
        val firstSwipe = TouchGesture.Moved(0.1f, 0.1f)
        val secondSwipe = TouchGesture.Moved(0.9f, 0.9f)

        val firstPending = coalescer.offer(firstSwipe)
        coalescer.startSwipe()
        val secondPending = coalescer.offer(secondSwipe)

        assertEquals(firstSwipe, firstPending?.take())
        assertEquals(secondSwipe, secondPending?.take())
    }

    @Test
    fun `a position after a completed read is queued again`() {
        val coalescer = TouchMoveCoalescer()

        coalescer.offer(TouchGesture.Moved(0.1f, 0.1f))?.take()

        assertTrue(coalescer.offer(TouchGesture.Moved(0.2f, 0.2f)) != null)
    }

    private companion object {
        const val SELECT = 6L
        const val MENU = 5L
        const val DOWN = 1L
        const val UP = 2L
    }
}
