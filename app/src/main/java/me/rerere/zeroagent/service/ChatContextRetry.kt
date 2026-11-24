package me.rerere.zeroagent.service

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage

/** A context overflow can be retried once, before text streams or after a completed tool round. */
internal fun shouldRetryContextLimit(
    allowRetry: Boolean,
    rangedGeneration: Boolean,
    autoCompactionEnabled: Boolean,
    failure: Throwable?,
    lastMessage: UIMessage?,
): Boolean = allowRetry &&
    !rangedGeneration &&
    autoCompactionEnabled &&
    failure?.isContextLimitError() == true &&
    (lastMessage?.role != MessageRole.ASSISTANT || lastMessage.getTools().any { it.isExecuted })

private fun Throwable.isContextLimitError(): Boolean {
    val markers = listOf(
        "context length",
        "context window",
        "maximum context",
        "max context",
        "context length exceeded",
        "maximum tokens",
        "prompt is too long",
        "prompt too long",
        "too many tokens",
        "token limit",
        "input is too long",
        "exceeds the model",
        "exceed.*context",
    )
    return generateSequence(this) { it.cause }
        .take(8)
        .any { cause ->
            val text = (cause.message.orEmpty() + " " + cause.toString())
                .lowercase()
                .replace('_', ' ')
            markers.any { marker ->
                if (marker.contains(".*")) Regex(marker).containsMatchIn(text) else marker in text
            }
        }
}
