package io.coreback.relayremote.protocol.session

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

sealed interface CommandResult {
    data object Executed : CommandResult
    data object DroppedStale : CommandResult
    data object RejectedFull : CommandResult
    data class Failed(val error: Throwable) : CommandResult
}

class OrderedCommandQueue<T>(
    scope: CoroutineScope,
    capacity: Int,
    private val execute: suspend (T) -> Unit,
) {
    private data class Queued<T>(
        val generation: Long,
        val value: T,
        val result: CompletableDeferred<CommandResult>,
    )

    private val generation = AtomicLong(0)
    private val channel = Channel<Queued<T>>(capacity)

    init {
        require(capacity > 0)
        scope.launch {
            for (item in channel) {
                if (item.generation != generation.get()) {
                    item.result.complete(CommandResult.DroppedStale)
                    continue
                }
                try {
                    execute(item.value)
                    item.result.complete(CommandResult.Executed)
                } catch (error: Throwable) {
                    item.result.complete(CommandResult.Failed(error))
                }
            }
        }
    }

    fun enqueue(value: T): Deferred<CommandResult> {
        val result = CompletableDeferred<CommandResult>()
        val accepted = channel.trySend(Queued(generation.get(), value, result)).isSuccess
        if (!accepted) result.complete(CommandResult.RejectedFull)
        return result
    }

    fun invalidate() {
        generation.incrementAndGet()
    }

    fun close() {
        invalidate()
        channel.close()
    }
}
