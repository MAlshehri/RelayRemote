package io.coreback.relayremote.protocol.session

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryPolicyTest {
    @Test
    fun `exponential retry is capped and full-jittered`() {
        val policy = RetryPolicy(
            baseDelayMillis = 500,
            maxDelayMillis = 8_000,
            maxAttempts = 8,
            random = Random(7),
        )

        val delays = (1..8).map(policy::delayForAttempt)

        delays.forEachIndexed { index, delay ->
            val cap = minOf(8_000L, 500L shl index)
            assertTrue(delay in 0..cap)
        }
        assertEquals(null, policy.delayForAttempt(9))
    }
}
