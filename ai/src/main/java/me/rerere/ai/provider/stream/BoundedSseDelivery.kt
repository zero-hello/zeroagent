package me.rerere.ai.provider.stream

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.ContinuationInterceptor

internal const val SSE_BUFFER_CAPACITY = 64

/** Only for OkHttp's asynchronous SSE reader: backpressure must stop reading, not queue jobs. */
internal fun <T> ProducerScope<T>.sendSseChunks(chunks: Iterable<T>): Boolean {
    try {
        for (chunk in chunks) {
            val immediate = trySend(chunk)
            if (immediate.isClosed) return false
            if (immediate.isFailure) {
                // Keep the producer Job for cancellation, but run the wait on this reader thread.
                runBlocking(coroutineContext.minusKey(ContinuationInterceptor)) { send(chunk) }
            }
        }
    } catch (interrupted: InterruptedException) {
        close(interrupted)
        Thread.currentThread().interrupt()
        return false
    } catch (_: CancellationException) {
        return false
    } catch (failure: Throwable) {
        close(failure)
        return false
    }
    return true
}
