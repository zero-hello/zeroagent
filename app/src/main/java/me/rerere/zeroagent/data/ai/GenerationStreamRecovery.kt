package me.rerere.zeroagent.data.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.providers.openai.ResponseStreamErrorException
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.StreamChunk
import me.rerere.ai.ui.StreamChunkHandler
import me.rerere.ai.util.HttpException
import me.rerere.zeroagent.data.datastore.NetworkSetting
import me.rerere.zeroagent.data.datastore.Settings

private const val TAG = "GenerationHandler"

internal const val GENERATION_STREAM_RETRY_INITIAL_DELAY_MS = 750L
internal const val GENERATION_STREAM_RETRY_MAX_DELAY_MS = 4_000L

/**
 * Upper bound for both stream-silence watchdogs, in seconds. Its only job is to keep a hand-typed
 * setting sane; the values themselves are user-configurable (Settings -> Network), defaulting to
 * 120s each. Kept in [NetworkSetting] so the settings screen clamps to the same number the
 * generation loop does.
 */
internal const val STREAM_SILENCE_MAX_SECONDS = NetworkSetting.MAX_WATCHDOG_SECONDS

/**
 * U3 — how many times a mid-stream break is patched by asking the model to continue from the text
 * it had already produced, before falling back to restarting the whole reply.
 */
internal const val MAX_STREAM_CONTINUATIONS = 2

/**
 * U3 — the synthetic user turn appended to a continuation request. It is sent to the provider but
 * never written back into the visible conversation, so the user only sees the streamed continuation
 * text (which merges into the same assistant message).
 */
internal const val CONTINUATION_INSTRUCTION =
    "Your previous reply was cut off by a network error. Continue it from exactly where it " +
        "stopped. Do not repeat anything you already wrote, do not restate the question, and do " +
        "not start over — just keep going to a natural end."

internal val USER_CANCELLATION_MARKERS = listOf(
    "canceled by user",
    "cancelled by user",
    "user_canceled",
    "user_cancelled",
)

// A deterministic 4xx will not succeed on retry, so retrying it just burns quota and delay for
// an outcome that was never going to change. These four are the exceptions: they signal a
// transient condition (timeout, conflict, precondition, rate limit) rather than a request that
// is permanently invalid.
internal val RETRYABLE_4XX_STATUS_CODES = setOf(408, 409, 425, 429)

internal fun isCancellationFailure(failure: Throwable): Boolean =
    generateSequence(failure) { it.cause }
        .take(8)
        .any { cause ->
            cause is CancellationException ||
                USER_CANCELLATION_MARKERS.any { marker ->
                    cause.message?.contains(marker, ignoreCase = true) == true
                }
        }

/**
 * A clean stream close only signals a transport failure worth retrying when NOTHING was ever
 * received. If at least one chunk arrived but none of them yielded parseable parts (e.g. every
 * part shape was unrecognized), that is a permanent condition - retrying the whole generation
 * cannot help, so the caller should log it and let the generation end normally instead of
 * synthesizing a retryable failure.
 */
internal fun shouldReportEmptyGenerationStream(receivedAnyChunk: Boolean): Boolean =
    !receivedAnyChunk

internal fun shouldRetryGenerationStreamFailure(
    failure: Throwable,
    retryAttempt: Long,
    maxRetries: Int,
    receivedMeaningfulOutput: Boolean,
): Boolean {
    if (receivedMeaningfulOutput || retryAttempt >= maxRetries.coerceAtLeast(0).toLong()) {
        return false
    }
    if (failure is ResponseStreamErrorException || isContextLimitFailure(failure)) {
        return false
    }
    if (isNonRetryableClientError(failure)) {
        return false
    }
    if (isQuotaExhaustedFailure(failure)) {
        return false
    }
    // Retry provider, parsing, and local processing failures alike. Cancellation is kept
    // out of the retry loop so stop-generation and parent-scope cancellation propagate.
    return !isCancellationFailure(failure)
}

/**
 * U3 — should a stream that broke AFTER the model had already written something be CONTINUED,
 * i.e. re-issued with the partial text and an instruction to keep going, instead of being handed
 * to the caller as a truncated reply?
 *
 * This is deliberately the mirror image of [shouldRetryGenerationStreamFailure]: that one refuses
 * to retry once output has arrived (a fresh retry would regenerate and duplicate text), while this
 * one only fires once output HAS arrived. The permanent conditions — an explicit response-stream
 * error, a context-limit failure, an exhausted quota, and a deterministic non-retryable 4xx — stay
 * non-continuable, because asking the model again cannot fix any of them.
 */
internal fun shouldContinueGenerationStream(
    failure: Throwable,
    receivedMeaningfulOutput: Boolean,
    continuationsUsed: Int,
    maxContinuations: Int = MAX_STREAM_CONTINUATIONS,
): Boolean {
    if (!receivedMeaningfulOutput) return false
    if (continuationsUsed >= maxContinuations.coerceAtLeast(0)) return false
    if (isCancellationFailure(failure)) return false
    if (failure is ResponseStreamErrorException || isContextLimitFailure(failure)) return false
    if (isNonRetryableClientError(failure)) return false
    if (isQuotaExhaustedFailure(failure)) return false
    return true
}

/**
 * U3 — can [partial] (the assistant message a broken stream left behind) seed a continuation
 * request? Only plain text is safe to continue: re-sending an assistant turn that holds a
 * half-streamed tool call would produce a request with a dangling tool call and no matching
 * result, which several providers reject outright.
 */
internal fun canContinueFromPartial(partial: UIMessage?): Boolean =
    partial != null &&
        partial.role == MessageRole.ASSISTANT &&
        partial.parts.none { it is UIMessagePart.ToolCall } &&
        partial.parts.any { it is UIMessagePart.Text && it.text.isNotBlank() }

/**
 * U3 — the request messages for a continuation attempt: the original system + context, then the
 * partial assistant turn, then a synthetic user instruction to keep going. The synthetic turn is
 * deliberately NOT written back into `messages`, so the visible conversation gains only the
 * streamed continuation text — merged into the same assistant message by [StreamChunkHandler].
 */
internal fun continuationRequestMessages(
    baseRequestMessages: List<UIMessage>,
    currentMessages: List<UIMessage>,
): List<UIMessage> {
    val partial = currentMessages.lastOrNull()
        ?.takeIf { it.role == MessageRole.ASSISTANT }
        ?: return baseRequestMessages
    return baseRequestMessages +
        UIMessage(role = MessageRole.ASSISTANT, parts = partial.parts) +
        UIMessage(
            role = MessageRole.USER,
            parts = listOf(UIMessagePart.Text(CONTINUATION_INSTRUCTION)),
            isSynthetic = true,
        )
}

/**
 * U3 — the final fallback: when a continuation is not possible, or has already been tried and the
 * stream broke again, restart the whole request ONCE from the pre-stream snapshot. The partial
 * reply is discarded, so the user sees the answer regenerate rather than stop mid-sentence.
 * [receivedMeaningfulOutput] here is the loop-wide flag, so this still fires when a continuation
 * attempt itself died before writing a single chunk.
 */
internal fun shouldRestartGenerationStreamAfterPartial(
    failure: Throwable,
    receivedMeaningfulOutput: Boolean,
    alreadyRestarted: Boolean,
): Boolean {
    if (!receivedMeaningfulOutput || alreadyRestarted) return false
    if (isCancellationFailure(failure)) return false
    if (failure is ResponseStreamErrorException || isContextLimitFailure(failure)) return false
    if (isNonRetryableClientError(failure)) return false
    if (isQuotaExhaustedFailure(failure)) return false
    return true
}

/**
 * U3 — the "we lost the connection, keeping your reply going" note shown in the chat's status line
 * while a continuation attempt runs. Plain mirror of [retryStatusText], but for the post-output
 * case, which never carries a failure reason the user needs to read.
 */
internal fun continueStatusText(
    context: Context,
    number: Int,
    max: Int,
): String = context.getString(me.rerere.zeroagent.R.string.chat_page_continuing, number, max)

// A 4xx other than the RETRYABLE_4XX_STATUS_CODES exceptions is deterministic: the same
// request will fail the same way on every retry. 5xx and failures with no known status code
// (most providers don't attach one) keep the existing retry behaviour.
internal fun isNonRetryableClientError(failure: Throwable): Boolean {
    val statusCode = generateSequence(failure) { it.cause }
        .take(8)
        .filterIsInstance<HttpException>()
        .firstOrNull()
        ?.statusCode
        ?: return false
    return statusCode in 400..499 && statusCode !in RETRYABLE_4XX_STATUS_CODES
}

// 429 is normally in RETRYABLE_4XX_STATUS_CODES because it usually signals ordinary rate
// limiting, which is worth retrying. But a 429 that also carries a RESOURCE_EXHAUSTED marker
// means the account is quota-blocked server-side (CCA returns this instantly): the same
// request will fail the same way on every retry, so retrying just burns time and requests.
internal val QUOTA_EXHAUSTED_MARKERS = listOf(
    "resource exhausted",
    "resource has been exhausted",
)

internal fun isQuotaExhaustedFailure(failure: Throwable): Boolean {
    val statusCode = generateSequence(failure) { it.cause }
        .take(8)
        .filterIsInstance<HttpException>()
        .firstOrNull()
        ?.statusCode
    if (statusCode != 429) {
        return false
    }
    return generateSequence(failure) { it.cause }
        .take(8)
        .any { cause ->
            val text = (cause.message.orEmpty() + " " + cause.toString())
                .lowercase()
                .replace('_', ' ')
            QUOTA_EXHAUSTED_MARKERS.any { marker -> marker in text }
        }
}

internal fun isContextLimitFailure(failure: Throwable): Boolean =
    generateSequence(failure) { it.cause }
        .take(8)
        .any { cause ->
            val text = (cause.message.orEmpty() + " " + cause.toString())
                .lowercase()
                .replace('_', ' ')
            "context length exceeded" in text ||
                "maximum context length" in text ||
                "maximum context window" in text
        }

internal fun generationStreamRetryDelayMs(retryAttempt: Long): Long =
    ((retryAttempt + 1) * GENERATION_STREAM_RETRY_INITIAL_DELAY_MS)
        .coerceAtMost(GENERATION_STREAM_RETRY_MAX_DELAY_MS)

internal fun retryFailureReason(failure: Throwable): String =
    generateSequence(failure) { it.cause }
        .mapNotNull { it.message?.trim()?.takeIf(String::isNotBlank) }
        .firstOrNull()
        ?.replace(Regex("\\s+"), " ")
        ?.take(240)
        ?: failure.javaClass.simpleName

internal fun retryStatusText(
    context: Context,
    retryNumber: Long,
    maxRetries: Int,
    failure: Throwable,
): String = context.getString(
    me.rerere.zeroagent.R.string.chat_page_retrying,
    retryNumber,
    maxRetries,
    retryFailureReason(failure),
)

/**
 * Poll [notifier] while a streamed reply is in flight and turn its verdict into the same
 * processing-status line the retry path already writes, so the chat can say "still working" or
 * "looks stalled" instead of counting seconds with no explanation. Never cancels anything: once
 * output has started the stream retry policy refuses to re-run the turn, so cancelling here would
 * only turn a slow-but-alive reply into a failure. See [StreamIdleNotifier].
 */
internal suspend fun watchStreamIdle(
    context: Context,
    notifier: StreamIdleNotifier,
    processingStatus: MutableStateFlow<String?>,
) {
    while (currentCoroutineContext().isActive) {
        delay(StreamIdleThresholds.POLL_MS)
        when (val notice = notifier.tick()) {
            null -> Unit
            StreamIdleNotice.Clear -> processingStatus.value = null
            is StreamIdleNotice.Waiting -> processingStatus.value = context.getString(
                me.rerere.zeroagent.R.string.chat_stream_waiting_first_output,
                notice.idleMs / 1000,
            )
            is StreamIdleNotice.Stalled -> processingStatus.value = context.getString(
                me.rerere.zeroagent.R.string.chat_stream_stalled,
                notice.idleMs / 1000,
            )
        }
    }
}

internal fun clearRetryStatus(processingStatus: MutableStateFlow<String?>) {
    processingStatus.value = null
}

// Marks the retry loop's "meaningful output already arrived" flag. Only chunks that carry
// actual model output (text/reasoning/tool/image content, or annotations) count - the bare
// Start/End markers and Usage/Finish bookkeeping chunks don't, mirroring the old
// choice.delta/message.parts.isNotEmpty() check against the pre-refactor chunk shape.
internal fun isMeaningfulStreamChunk(chunk: StreamChunk): Boolean = when (chunk) {
    is StreamChunk.TextDelta,
    is StreamChunk.ReasoningDelta,
    is StreamChunk.ToolCallDelta,
    is StreamChunk.ImageDelta,
    is StreamChunk.ImageSnapshot,
    is StreamChunk.ServerToolStart,
    is StreamChunk.ServerToolInputDelta,
    is StreamChunk.ServerToolEnd,
    is StreamChunk.Annotations -> true
    else -> false
}

internal suspend fun <T> retryGenerationTransportRequest(
    maxRetries: Int,
    onRetry: (retryNumber: Long, failure: Throwable) -> Unit = { _, _ -> },
    request: suspend () -> T,
): T {
    var retryAttempt = 0L
    while (true) {
        try {
            return request()
        } catch (failure: Throwable) {
            if (!shouldRetryGenerationStreamFailure(
                    failure = failure,
                    retryAttempt = retryAttempt,
                    maxRetries = maxRetries,
                    receivedMeaningfulOutput = false,
                )) {
                throw failure
            }
            val delayMs = generationStreamRetryDelayMs(retryAttempt)
            Log.w(
                TAG,
                "generateText: retrying after failure " +
                    "(${retryAttempt + 1}/$maxRetries) in ${delayMs}ms",
                failure,
            )
            onRetry(retryAttempt + 1, failure)
            delay(delayMs)
            retryAttempt++
        }
    }
}

