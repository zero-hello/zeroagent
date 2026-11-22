package me.rerere.tts.provider.providers

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout

/** An engine deadline is an error; cancelling the caller still cancels synthesis. */
internal suspend fun <T> withSystemTtsDeadline(millis: Long, block: suspend () -> T): T = try {
    withTimeout(millis) { block() }
} catch (error: TimeoutCancellationException) {
    currentCoroutineContext().ensureActive()
    throw IllegalStateException("tts_engine_timeout", error)
}
