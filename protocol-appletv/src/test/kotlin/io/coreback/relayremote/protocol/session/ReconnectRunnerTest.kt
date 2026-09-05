package io.coreback.relayremote.protocol.session

import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconnectRunnerTest {
    @Test
    fun `retries in order and succeeds after transient failures`() = runTest {
        val attempts = mutableListOf<Int>()
        val waits = mutableListOf<Long>()
        var connections = 0
        val runner = ReconnectRunner(
            retryPolicy = RetryPolicy(
                baseDelayMillis = 10,
                maxDelayMillis = 100,
                maxAttempts = 4,
                random = Random(9),
            ),
            wait = { waits += it },
        )

        val connected = runner.run(
            onAttempt = { attempts += it },
            connect = {
                connections++
                if (connections < 3) error("network unavailable")
            },
        )

        assertTrue(connected)
        assertEquals(listOf(1, 2, 3), attempts)
        assertEquals(3, waits.size)
    }

    @Test
    fun `terminal failure stops reconnect attempts`() = runTest {
        var connections = 0
        val runner = ReconnectRunner(
            retryPolicy = RetryPolicy(
                baseDelayMillis = 10,
                maxDelayMillis = 100,
                maxAttempts = 4,
                random = Random(9),
            ),
            wait = {},
        )

        val connected = runner.run(
            onAttempt = {},
            shouldRetry = { it !is SecurityException },
            connect = {
                connections++
                throw SecurityException("Credential rejected")
            },
        )

        assertTrue(!connected)
        assertEquals(1, connections)
    }
}
