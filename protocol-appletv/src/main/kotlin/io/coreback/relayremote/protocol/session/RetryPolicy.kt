package io.coreback.relayremote.protocol.session

import kotlin.random.Random

class RetryPolicy(
    private val baseDelayMillis: Long = 500,
    private val maxDelayMillis: Long = 30_000,
    private val maxAttempts: Int = 8,
    private val random: Random = Random.Default,
) {
    init {
        require(baseDelayMillis > 0)
        require(maxDelayMillis >= baseDelayMillis)
        require(maxAttempts > 0)
    }

    /** Returns full-jitter delay for a one-based attempt, or null when exhausted. */
    fun delayForAttempt(attempt: Int): Long? {
        if (attempt !in 1..maxAttempts) return null
        var cap = baseDelayMillis
        repeat(attempt - 1) {
            cap = if (cap >= maxDelayMillis / 2) maxDelayMillis else cap * 2
        }
        cap = minOf(cap, maxDelayMillis)
        return random.nextLong(cap + 1)
    }
}
