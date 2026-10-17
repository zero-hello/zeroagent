package me.rerere.zeroagent.assistant

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** A cancelled provider reply must leave THINKING; closing this session must stay cancelled. */
internal suspend fun assistantReply(block: suspend () -> String?): Result<String> = try {
    Result.success(block().orEmpty())
} catch (error: CancellationException) {
    currentCoroutineContext().ensureActive()
    Result.failure(error)
} catch (error: Throwable) {
    Result.failure(error)
}
