package io.coreback.relayremote.protocol.session

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

class ReconnectRunner(
    private val retryPolicy: RetryPolicy,
    private val wait: suspend (Long) -> Unit = { delay(it) },
) {
    suspend fun run(
        onAttempt: (Int) -> Unit,
        shouldRetry: (Throwable) -> Boolean = { true },
        connect: suspend () -> Unit,
    ): Boolean {
        var attempt = 1
        while (true) {
            val waitMillis = retryPolicy.delayForAttempt(attempt) ?: return false
            onAttempt(attempt)
            wait(waitMillis)
            try {
                connect()
                return true
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (!shouldRetry(error)) return false
                attempt++
            }
        }
    }
}
