package io.coreback.relayremote.protocol.session

import java.util.Collections
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OrderedCommandQueueTest {
    @Test
    fun `commands execute in submission order`() = runTest {
        val executed = Collections.synchronizedList(mutableListOf<Int>())
        val queue = OrderedCommandQueue<Int>(backgroundScope, capacity = 8) {
            executed += it
        }

        val results = (1..4).map { queue.enqueue(it) }
        runCurrent()

        assertEquals(listOf(1, 2, 3, 4), executed)
        assertEquals(
            List(4) { CommandResult.Executed },
            results.map { it.await() },
        )
    }

    @Test
    fun `disconnect generation drops commands that have not started`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val executed = mutableListOf<Int>()
        val queue = OrderedCommandQueue<Int>(backgroundScope, capacity = 8) {
            if (it == 1) gate.await()
            executed += it
        }
        val first = queue.enqueue(1)
        val stale = queue.enqueue(2)
        runCurrent()

        queue.invalidate()
        gate.complete(Unit)
        runCurrent()

        assertEquals(CommandResult.Executed, first.await())
        assertEquals(CommandResult.DroppedStale, stale.await())
        assertEquals(listOf(1), executed)
    }
}
